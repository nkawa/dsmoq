package dsmoq.framework.types;

/**
 * ...
 * @author terurou
 */
typedef PromiseStream<T> = {
    function then(handler: T -> Void): Void;
}