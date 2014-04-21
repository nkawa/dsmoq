package dsmoq.framework.types;

import promhx.Stream;

/**
 * @author terurou
 */
typedef Rendered<TState, TEvent> = {
    html: Html,
    event: Stream<TEvent>,
    state: Void -> TState
}