package dsmoq.framework.helpers;

import promhx.Promise;

class Promises {
    public static function connect<A>(p: Promise<A>, p2: Promise<A>){
        p.then(function(x){p2.resolve(x);});
    }

    public static function oneOf<A>(promises:Array<Promise<A>>): Promise<A>{
        var promise = new Promise();
        Lambda.iter(promises, function(p){p.then(promise.resolve);});
        return promise;
    }

    public static function value<A>(x: A): Promise<A>{
        return tap(function(p){p.resolve(x);});
    }

    public static function void<A>(): Promise<A> {      // TODO: to be lazy evaluation
        return new Promise();
    }

    public static inline function tap<A>(f: Promise<A> -> Void){
        var promise = new Promise();
        f(promise);
        return promise;
    }
}