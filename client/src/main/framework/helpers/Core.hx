package framework.helpers;

import framework.Types;

class Core{
    public static function identity<A>(x:A):A{return x;}
    public static function effect<A>(f: A -> Void):A -> A{return function(x:A){f(x); return x;};}
    public static function nop<A>():A{ return null;}
    public static function ignore<A>(x:A):Void{ return;}
    public static function toState<A>(xs: Array<Void -> A>): Void -> Array<A>{
        return function(){return xs.map(function(s){return s();});};
    }
    public static function tap<A, B>(f: A -> B, g: A -> Void){
        return function(x: A){
            g(x);
            return f(x);
        }
    }

    // not deep copy
    public static function merge<A>(a: Dynamic, b: Dynamic):A{
        function copy(org: Dynamic, o: Dynamic){
            Lambda.iter(Reflect.fields(o), function(fieldName){
                var value = Reflect.field(o, fieldName);
                Reflect.setField(org, fieldName, value);
            });
        }
        var ret = untyped {};
        copy(ret, a);
        copy(ret, b);
        return ret;
    }

    public static function isNone<A>(o: Option<A>){
        return Type.enumEq(None, o);
    }
    public static function option<A>(x: A): Option<A>{
        return (x == null) ? None : Some(x);
    }
    public static function each<A>(o: Option<A>, f: A -> Void): Void{
        if(o == null) return;
        return switch(o){
            case Some(x): if(x != null) f(x);
            case None:
        };
    }
    public static function getOrElse<A>(o: Option<A>, f: Void -> A): A{
        if(o == null) return f();
        return switch(o){
            case Some(x): (x == null) ? f() : x;
            case None: f();
        };
    }
}
