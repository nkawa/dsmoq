package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Component<TIn, TState, TEvent> = {
    render: TIn -> Rendered<TState, TEvent>
}