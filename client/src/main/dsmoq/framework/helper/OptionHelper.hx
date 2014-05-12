package dsmoq.framework.helper;

import dsmoq.framework.types.Option;

/**
 * ...
 * @author terurou
 */
class OptionHelper {
    public static inline function toOption<T>(x: T) : Option<T> {
        return (x == null) ? None: Some(x);
    }

    public static inline function getOrElse<T>(a: Option<T>, b: T): T {
        return switch (a) {
            case Some(x): x;
            case None: b;
        }
    }

    public static inline function getOrThrow<T>(a: Option<T>, error: Dynamic): T {
        return switch (a) {
            case Some(x): x;
            case None: throw error;
        }
    }

    public static inline function each<T>(x: Option<T>, f: T -> Void): Void {
        switch (x) {
            case Some(a): f(a);
            case None:
        }
    }

    public static inline function map<A, B>(x: Option<A>, f: A -> B) : Option<B> {
        return switch (x) {
            case Some(a): Some(f(a));
            case None: None;
        }
    }

    public static inline function bind<A, B>(x: Option<A>, f: A -> Option<B>) {
        return switch (x) {
            case Some(a): f(a);
            case None: None;
        }
    }
}