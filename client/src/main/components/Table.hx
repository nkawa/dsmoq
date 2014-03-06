package components;
import promhx.Promise;
import framework.Types;
import framework.JQuery;
import framework.helpers.*;
using framework.helpers.Components;
using framework.helpers.Rendereds;

typedef RowStrings = Array<String>

typedef Row = Component<RowStrings, RowStrings, Void>
typedef RowWithAction = Component<RowInput, RowStrings, TableAction>

enum TableActionSelection { Insert; Update; Delete; }

enum TableAction {
    UpdateRow(idx: Int, row: RowStrings);
    InsertRow(idx: Int, row: RowStrings);
    DeleteRow(idx: Int);
}

private typedef RowInput = {index: Int, rowStrings: RowStrings}

class Table{
    static inline var DELETE_BUTTON_CLASS = "component-delete-button";
    static inline var INSERT_BUTTON_CLASS = "component-insert-button";

    public static function create(name, row: Array<Component<String,String, Void>>):Component<Array<RowStrings>, Array<RowStrings>, Void>{
        var rowComponent = Components.group(row, JQuery.join('<td></td>'));
        return Components.list(rowComponent, JQuery.join('<tr></tr>')).decorate(JQuery.wrapBy.bind('<table><tbody></tbody></table>'));
    }

    public static function editable(name, 
        row:      Array<Component<String, String, Void>>,
        inputRow: Array<Component<String, String, Void>>,
        defaults: RowStrings,
        atLeastOne = false 
    ){
        var rowComponent = Components.group(row, JQuery.join('<td></td>'))
            .decorate(JQuery.add.bind('<td><a class="btn btn-warning btn-sm $DELETE_BUTTON_CLASS">del</a></td>'));

        var inputRowComponent = Components.group(inputRow, JQuery.join('<td></td>'))
            .decorate(JQuery.add.bind('<td><a class="btn btn-success btn-sm $INSERT_BUTTON_CLASS">add</a></td>'));

        function render(xs: Array<RowStrings>){
            var renderedInputRow = inputRowComponent.render(defaults).decorate(JQuery.wrapBy.bind('<tr></tr>'));
            var rendereds = xs.map(rowComponent.render);
            var rowStates = rendereds.map(function(r){return r.state;});
            var body = JQuery.wrapBy('<tbody></tbody>', JQuery.join('<tr></tr>')(rendereds.htmls()).add(renderedInputRow.html));
            function removeRow(element: Dynamic){
                var index = body.find('.$DELETE_BUTTON_CLASS').index(untyped __js__('this'));
                body.find('tr:nth-child(${index+1})').remove();
                rowStates.splice(index, 1);
            }
            function addRow(newInput: RowStrings){
                var newRow = rowComponent.render(newInput).decorate(JQuery.wrapBy.bind('<tr></tr>'));
                newRow.html.insertBefore(renderedInputRow.html);
                newRow.html.find('.$DELETE_BUTTON_CLASS').on("click", removeRow);
                rowStates.push(newRow.state);
                renderedInputRow.html.remove();
                renderedInputRow = inputRowComponent.render(defaults).decorate(JQuery.wrapBy.bind('<tr></tr>'));
                body.append(renderedInputRow.html);
                renderedInputRow.html.find('.$INSERT_BUTTON_CLASS').on("click", function(_){
                    addRow(renderedInputRow.state());
                });
            }

            renderedInputRow.html.find('.$INSERT_BUTTON_CLASS').on("click", function(_){
                addRow(renderedInputRow.state());
            });
            body.find('.$DELETE_BUTTON_CLASS').on("click", removeRow);
            return {
                html: body,
                state: Core.toState(rowStates),
                event: Promises.void()
            }
        }
        return Components.toComponent(render);
    }
}
