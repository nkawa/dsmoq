package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Replacable<TIn, TState, TEvent> = {>Component<TState, TEvent>, put: TIn -> Void}
