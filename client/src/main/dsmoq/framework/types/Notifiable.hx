package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Notifiable<T> = {
    function notify(message: T): Void;
}