package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef PlaceHolder<TIn, TState, TEvent> = {
    render: TIn -> Replacable<TIn, TState, TEvent>
}
