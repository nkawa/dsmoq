package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Replacable<TIn, TState, TEvent> = {> Rendered<TState, TEvent>, put: TIn -> Void}
