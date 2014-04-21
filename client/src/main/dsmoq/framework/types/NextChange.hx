package dsmoq.framework.types;

/**
 * @author terurou
 */
enum NextChange<I,O> { Inner(a: I); Outer(b: O); }