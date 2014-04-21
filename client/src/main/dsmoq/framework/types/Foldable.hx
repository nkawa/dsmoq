package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Foldable<TIn, TState, Out> = ComponentFactory<TIn, TState, NextChange<TIn, Out>>;
