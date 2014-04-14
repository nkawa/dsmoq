package dsmoq.pages;

import dsmoq.framework.Types;
import dsmoq.components.ConnectionPanel;

using dsmoq.framework.helpers.Components;
import dsmoq.framework.JQuery;
import dsmoq.framework.helpers.Connection;
import dsmoq.framework.Effect;
import dsmoq.framework.helpers.Core;

import promhx.Stream;

class Common{
    public static function waiting(html){
        html.text("waiting...");
    }
    public static function observe<Input, Output>(comp: PlaceHolder<Input, ConnectionStatus, Output>): Component<Input, Void, Output>{
        function render(x: Input){
            var rendered = comp.render(x);
            Effect.global().observeConnection(rendered.state);
            rendered.state = Core.nop;  // never effect type by this assignment
            return untyped rendered;
        }
        return Components.toComponent(render);
    }

    public static function connectionPanel<Input, Output>(name: String, comp: Component<Input, Void, Output>, request: HttpRequest): Rendered<Void, Output>{
        var c: PlaceHolder<HttpRequest, ConnectionStatus, Output> = ConnectionPanel.request(waiting, name, comp, Effect.global().notifyError.bind(_, null));
        return observe(c).render(request);
    }

    public static function textfield(fieldName){
        return Components.fromHtml(function(input){return JQuery.j('<input name="$fieldName" class="form-control" type="text" value="$input"></input>');})
            .state(function(html){return html.val();});
    }

    public static var label =
        Components.fromHtml(function(input){return JQuery.j('<p class="form-control-static">$input</p>');})
            .state(function(html){return html.text();});

    public static function select(fieldName, xs: Array<{value: String, displayName: String}>) {
        function selected(x, y){
            return (x == y) ? "selected" : "";
        }
        return Components.fromHtml(function(input: String){ return JQuery.gather(JQuery.j('<select name="$fieldName"></select>'))
            (xs.map(function(x){return JQuery.j('<option value="${x.value}" ${selected(input, x.value)}>${x.displayName}</option>');}));})
            .state(function(html){return html.val();});
    }

    public static function radio(fieldName, xs: Array<{value: String, displayName: String}>){
        function isChecked(x, y){
            return (x == y) ? 'checked=""': '';
        }
        function val(html:Html){
            return html.find(":checked").val();
        }
        return Components.fromHtml(function(input: String){
            return JQuery.join('<div class="radio"></div>')(
                xs.map(function(x){ return
                    JQuery.j('<label><input type="radio" value=${x.value} ${isChecked(x.value, input)} name="$fieldName"></input>${x.displayName}</label>');
                })
            );
        }).state(val);
    }

    public static function withRequest<Input, State, Output>(component: Component<Input, State, Output>, eventName: String, f: Stream<State> -> Stream<Signal>, selector = "*"){
        return component.decorateWithState(function(html, state: Void -> State){
            var stream = new Stream();
            f(stream).then(function(_){
                html.removeClass("disabled");
            });
            html.addClass("can-disable");
            JQuery.findAll(html, selector).on(eventName, function(x){
                html.addClass("disabled");
                stream.resolve(state());
            });

            return html;
        });
    }
    public static function displayStringForUser(user){
        return '${user.organization} ${user.fullname} (${user.name})';
    }

    public static function fileViewModel(file, canDownload=true){
        return {
            name: file.name,
            description: file.description,
            size: file.size,
            uploadedBy: displayStringForUser(file.updatedBy),
            canDownload: canDownload
        };
    }

    public static function displayStringForAccess(acl){
        return switch(Std.string(acl)){
            case "3": "Owner";
            case "2": "Full Public";
            case "1": "Limited Public";
            default: throw "Unknown Value for ACL:" + acl;
        }
    }
}
