package dsmoq.components;

import dsmoq.framework.Types;
import dsmoq.framework.helpers.*;
import dsmoq.framework.helpers.Connection;
import dsmoq.framework.helpers.Promises;
import dsmoq.framework.JQuery;
import promhx.Promise;

class LoadingPanel {
    private static function ignoreError(x: Dynamic){ trace(x);}

    public static function create<A, B, State, Output>(
        waiting: Html -> Void,
        name: String,
        component: Component<B, Void, NextChange<A,Output>>,
        f: A -> {event: Promise<B>, state: Void -> State},
        onError: Dynamic -> Void = null
    ): PlaceHolder<A, State, Output> {
        if (onError == null) onError = ignoreError;

        var foldable = Components.toComponent(function (a) {
            var fa = f(a);
            var body = JQuery.div();
            waiting(body);
            fa.event.catchError(onError);
            var p = fa.event.pipe(function(b){
                var rendered = component.render(b);
                body.empty().append(rendered.html);
                return rendered.event;
            });
            return {html: body, state: fa.state, event: p};
        });

        return PlaceHolders.create(name, foldable);
    }
}
