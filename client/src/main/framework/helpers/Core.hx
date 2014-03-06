package framework.helpers;

class Core{
    public static function identity<A>(x:A):A{return x;}
    public static function effect<A>(f: A -> Void):A -> A{return function(x:A){f(x); return x;};}
    public static function nop<A>():A{ throw "nop called"; return null;}
    public static function ignore<A>(x:A):Void{ return;}
    public static function toState<A>(xs: Array<Void -> A>): Void -> Array<A>{
        return function(){return xs.map(function(s){return s();});};
    }

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
}
