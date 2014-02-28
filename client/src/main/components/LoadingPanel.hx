package components;

import framework.Types;
import framework.helpers.*;
import framework.helpers.Connection;
import framework.helpers.Promises;
import framework.JQuery;
import promhx.Promise;

class LoadingPanel {
    public static function create<A, B, State, Output>(
        waiting: Html -> Void,
        name: String,
        component: Component<B, Void, NextChange<A,Output>>,
        f: A -> {event: Promise<B>, state: Void -> State}
    ): PlaceHolder<A, State, Output>{
        function render(a){
            var fa = f(a);
            var body = JQuery.div();
            waiting(body);
            var p = fa.event.pipe(function(b){
                var rendered = component.render(b);
                body.empty().append(rendered.html);
                return rendered.event;
            });
            return {html: body, state: fa.state, event: p};
        }
        var foldable = Components.toComponent(render);
        return PlaceHolders.create(name, foldable);
    }
}