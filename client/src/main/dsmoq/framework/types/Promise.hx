package dsmoq.framework.types;

/**
 * ...
 * @author terurou
 */
typedef Promise<T> = {
    function isResolved(): Bool;
    function isRejected(): Bool;

    function then(handler: T -> Void): Void;
}