package framework.helpers;

class Core{
    public static function identity(x){return x;}
    public static function effect(f){return function(x){f(x); return x;};}
    public static function nop<A>():A{ throw "nop called"; return null;}
}
