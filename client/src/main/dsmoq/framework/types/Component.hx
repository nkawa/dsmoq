package dsmoq.framework.types;

import promhx.Stream;

/**
 * @author terurou
 */
typedef Component<TState, TEvent> = {
    html: Html,
    event: Stream<TEvent>,
    state: Void -> TState
}