package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Foldable<TIn, TState, Out> = Component<TIn, TState, NextChange<TIn, Out>>;
