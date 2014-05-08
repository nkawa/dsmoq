package dsmoq.framework.types;

import js.html.Node;
import promhx.Stream;

/**
 * @author terurou
 */
typedef Component<TMessage, TState, TEvent> = {
    function notify(message: TMessage): Void;
    function state(): TState;
    function then(handler: TEvent -> Void): Void;
    function appendTo(appendFn: Node -> Void): Void;
}