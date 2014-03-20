package components;
import promhx.Promise;
import framework.Types;
import framework.JQuery;
import framework.helpers.*;
using framework.helpers.Components;
using framework.helpers.Rendereds;

typedef RowStrings = Array<String>

typedef Row = Component<RowStrings, RowStrings, Void>

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
                afterRendering(html);
                html = JQuery.wrapBy('<tbody></tbody>', html);
                if(header != null){
                    function td(x){
                        return JQuery.j('<td>$x</td>');
                    }
                    html = JQuery.j('<thead></thead>').append(JQuery.gather(JQuery.j('<tr></tr>'))(header.map(td)))
                    .add(html);
                }

                return JQuery.wrapBy('<table class="table table-bordered table-condensed"></table>', html);
            });
    }

    public static function editable(name, 
        row:      Array<Component<String, String, Void>>,
        inputRow: Array<Component<String, String, Void>>,
        defaults: RowStrings,
        atLeastOne = false 
    ){
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
            function removeRow(element: Dynamic){
                var buttons = body.find('.$DELETE_BUTTON_CLASS');
                if(!(atLeastOne && buttons.length == 1)){
                    var index = buttons.index(untyped __js__('this'));
                    body.find('tr:nth-child(${index+1})').remove();
                    rowStates.splice(index, 1);
                    afterRendering(body);
                }
            }
            function addRow(newInput: RowStrings){
                var newRow = rowComponent.render(newInput).decorate(JQuery.wrapBy.bind('<tr></tr>'));
                newRow.html.insertBefore(renderedInputRow.html);
                newRow.html.find('.$DELETE_BUTTON_CLASS').on("click", removeRow);
                rowStates.push(newRow.state);
                renderedInputRow.html.remove();
                renderedInputRow = inputRowComponent.render(defaults).decorate(JQuery.wrapBy.bind('<tr></tr>'));
                body.append(renderedInputRow.html);
                afterRendering(body);
                renderedInputRow.html.find('.$INSERT_BUTTON_CLASS').on("click", function(_){
                    addRow(renderedInputRow.state());
                });
            }

            renderedInputRow.html.find('.$INSERT_BUTTON_CLASS').on("click", function(_){
                addRow(renderedInputRow.state());
            });
            body.find('.$DELETE_BUTTON_CLASS').on("click", removeRow);
            afterRendering(body);
            return {
                html: JQuery.wrapBy('<table class="table table-bordered table-condensed"></table>', body),
                state: Core.toState(rowStates),
                event: Promises.void()
            }
        }
        return Components.toComponent(render);
    }

    private static function afterRendering(html){
        numbering(html);
        hideHiddenCell(html);
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
