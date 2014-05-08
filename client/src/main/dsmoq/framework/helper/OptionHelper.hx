package dsmoq.framework.helper;

import dsmoq.framework.types.Option;

/**
 * ...
 * @author terurou
 */
class OptionHelper {
    public static function toOption<T>(x: T) : Option<T> {
        return (x == null) ? None: Some(x);
    }

    public static function map<A, B>(x: Option<A>, f: A -> B) : Option<B> {
        return switch (x) {
            case Some(a): Some(f(a));
            case None: None;
        }
    }

    public static function bind<A, B>(x: Option<A>, f: A -> Option<B>) {
        return switch (x) {
            case Some(a): f(a);
            case None: None;
        }
    }
}