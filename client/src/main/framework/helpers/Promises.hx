package framework.helpers;

import promhx.Promise;

class Promises{
    public static function connect<A>(p: Promise<A>, p2: Promise<A>){
        p.then(function(x){p2.resolve(x);});
    }

    public static function oneOf<A>(promises:Array<Promise<A>>): Promise<A>{
        var promise = new Promise();
        Lambda.iter(promises, function(p){p.then(promise.resolve);});
        return promise;
    }

    public static function void<A>(): Promise<A> {      // TODO: to be lazy evaluation
        var promise = new Promise();
        promise.reject("You can't call Promise<Void>.");
        return promise;
    }
}
