package framework.helpers;

class Core{
    public static function identity(x){return x;}
    public static function effect(f){return function(x){f(x); return x;};}
    public static function nop<A>():A{ throw "nop called"; return null;}

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
