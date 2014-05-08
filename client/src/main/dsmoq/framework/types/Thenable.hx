package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Thenable<T> = {
    function then(x: T): Void;
}