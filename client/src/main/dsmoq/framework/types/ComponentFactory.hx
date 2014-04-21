package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef ComponentFactory<TIn, TState, TEvent> = {
    render: TIn -> Component<TState, TEvent>
}