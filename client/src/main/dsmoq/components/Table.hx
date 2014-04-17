package dsmoq.components;

import promhx.Promise;
import promhx.Stream;
import dsmoq.framework.Types;
import dsmoq.framework.JQuery;
import dsmoq.framework.helpers.*;
using dsmoq.framework.helpers.Components;
using dsmoq.framework.helpers.Rendereds;

typedef RowStrings = Array<String>

typedef Row = Component<RowStrings, RowStrings, Void>

typedef TableAction = {
    onDelete: RowStrings -> Promise<Option<RowStrings>>,
    onAdd:    RowStrings -> Promise<Option<RowStrings>>,
    ?additional: {selector: String, action: Html -> (Html -> RowStrings) -> Stream<Signal>}
}

class Table{
    static inline var DELETE_BUTTON_CLASS = "component-table-delete-button";
    static inline var INSERT_BUTTON_CLASS = "component-table-insert-button";

    static inline var HIDDEN_CELL_CLASS = "component-table-hidden-cell";

    public static var hiddenCell:Component<String, String, Void> = Components.fromHtml(function(s){
        return JQuery.div().attr("class", HIDDEN_CELL_CLASS).text(s);
    }).state(function(html){return html.text();});

    public static function create(name, row: Array<Component<String,String, Void>>, header: Array<String> = null):Component<Array<RowStrings>, Array<RowStrings>, Void>{
        var rowComponent = Components.group(row, JQuery.join('<td></td>'))
            .decorate(function(html){
                return JQuery.j('<td class="col-min-width table-numbering">0</td>').add(html);
            });

        return Components.list(rowComponent, JQuery.join('<tr></tr>'))
            .decorate(function(html){
                html = JQuery.wrapBy('<tbody></tbody>', html);
                if(header != null){
                    function td(x){
                        return JQuery.j('<td>$x</td>');
                    }
                    html = JQuery.j('<thead></thead>').append(JQuery.gather(JQuery.j('<tr></tr>'))(header.map(td)))
                    .add(html);
                }
                numbering(html);

                return JQuery.wrapBy('<table class="table table-bordered table-condensed"></table>', html);
            });
    }

    private static var defaultActions: TableAction = {
        onDelete: function(xs: RowStrings){return Promises.value(Some(xs));},
        onAdd:    function(xs: RowStrings){return Promises.value(Some(xs));}
    }

    public static function editable(name,
        row:      Array<Component<String, String, Void>>,
        inputRow: Array<Component<String, String, Void>>,
        defaults: RowStrings,
        actions:TableAction = null,
        atLeastOne = false
    ){
        if(actions == null) actions = defaultActions;
        var selector = '.$DELETE_BUTTON_CLASS, .$INSERT_BUTTON_CLASS';
        if(actions.additional!= null){
            selector = selector + ', ${actions.additional.selector}';
        }

        function addColumn(isInsert, row){
            var klass = isInsert ? 'btn-success $INSERT_BUTTON_CLASS' : 'btn-warning $DELETE_BUTTON_CLASS';
            var message = isInsert ? "add" : "del";
            var head = isInsert ? '<td class="col-min-width">*</td>' : '<td class="table-numbering">0</td>';

            return JQuery.j(head)
                    .add(row)
                    .add(JQuery.j('<td><a class="btn btn-sm $klass">$message</a></td>'));
        }
        var rowComponent = Components.group(row, JQuery.join('<td></td>'))
            .decorate(addColumn.bind(false));

        var inputRowComponent = Components.group(inputRow, JQuery.join('<td></td>'))
            .decorate(addColumn.bind(true));


        function render(xs: Array<RowStrings>){
            var renderedInputRow = inputRowComponent.render(defaults).decorate(JQuery.wrapBy.bind('<tr></tr>'));
            var rendereds = xs.map(rowComponent.render);
            var rowStates = rendereds.map(function(r){return r.state;});
            var body = JQuery.wrapBy('<tbody></tbody>', JQuery.join('<tr></tr>')(rendereds.htmls()).add(renderedInputRow.html));
            function errorProcess(msg){
                dsmoq.framework.Effect.global().notifyError(msg);
                enableButtons(body, selector);
            }
            function ifTrue(p: Promise<Option<RowStrings>>, f: RowStrings-> Void, final: Void -> Void = null){
                p.then(function(b){
                    Core.each(b, f);
                    if(final != null) final();
                }).catchError(errorProcess);
            }
            function removeRow(element: Dynamic){
                var buttons = body.find('.$DELETE_BUTTON_CLASS');
                if(!(atLeastOne && buttons.length == 1)){
                    disableButtons(body, selector);
                    var index = buttons.index(untyped JQuery.self().button('loading'));
                    ifTrue(actions.onDelete(rowStates[index]()), function(_){
                        body.find('tr:nth-child(${index+1})').remove();
                        rowStates.splice(index, 1);
                        afterRendering(body, selector);
                    });
                }
            }
            var addRow;

            function refreshInputRow(){
                renderedInputRow.html.remove();
                renderedInputRow = inputRowComponent.render(defaults).decorate(JQuery.wrapBy.bind('<tr></tr>'));
                body.append(renderedInputRow.html);
                afterRendering(body, selector);
                renderedInputRow.html.find('.$INSERT_BUTTON_CLASS').on("click", function(_){
                    addRow(renderedInputRow.state(), JQuery.self());
                });
            }

            function beforeAdditionalAction(rowHtml: Html){
                disableButtons(body, selector);
                var index = body.find(actions.additional.selector).index(rowHtml);
                return rowStates[index]();
            }

            addRow = function(newInput: RowStrings, b: Html){
                disableButtons(body, selector);
                (untyped b).button("loading");
                ifTrue(actions.onAdd(newInput), function(xs){
                    var newRow = rowComponent.render(xs).decorate(JQuery.wrapBy.bind('<tr></tr>'));
                    newRow.html.insertBefore(renderedInputRow.html);
                    newRow.html.find('.$DELETE_BUTTON_CLASS').on("click", removeRow);
                    if(actions.additional!= null){
                        var targetSelector = actions.additional.selector;
                        actions.additional.action(newRow.html.find(targetSelector), beforeAdditionalAction).then(function(_){
                            afterRendering(body, selector);
                        }).catchError(errorProcess);
                    }
                    rowStates.push(newRow.state);
                }, refreshInputRow);
            }
            renderedInputRow.html.find('.$INSERT_BUTTON_CLASS').on("click", function(_){
                addRow(renderedInputRow.state(), JQuery.self());
            });
            if(actions.additional!= null){
                var targetSelector = actions.additional.selector;
                actions.additional.action(body.find(targetSelector), beforeAdditionalAction).then(function(_){
                    afterRendering(body, selector);
                }).catchError(errorProcess);
            }
            body.find('.$DELETE_BUTTON_CLASS').on("click", removeRow);
            afterRendering(body, selector);
            return {
                html: JQuery.wrapBy('<table class="table table-bordered table-condensed"></table>', body),
                state: Core.toState(rowStates),
                event: new Stream()
            }
        }
        return Components.toComponent(render);
    }

    private static function afterRendering(html, selector){
        numbering(html);
        hideHiddenCell(html);
        enableButtons(html, selector);
    }
    private static function disableButtons(html: Html, selector){
        html.find(selector).addClass("disabled");
    }

    private static function enableButtons(html: Html, selector){
        (untyped html.find(selector).removeClass("disabled").filter('a, button')).button('reset');
    }

    private static function numbering(html: Html){
        var targets = html.find(".table-numbering");

        for(i in 0...targets.length){
            (untyped targets)[i].innerHTML = i + 1;
        }
    }

    private static function hideHiddenCell(html: Html){
        html.find('.$HIDDEN_CELL_CLASS').parent().hide();
    }
}
