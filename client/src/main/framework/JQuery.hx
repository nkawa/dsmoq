package framework;

import framework.Types;

extern class Jq{
    public function append(jq: Jq):Jq;
    public function add(jq: Jq):Jq;
    public function find(selector: String):Jq;
    @:overload(function (f: Int -> Dynamic -> Bool): Jq{})
    public function filter(selector: String):Jq;
    public function html(text: String):Jq;
    @:overload(function ():String{})
    public function text(text: String):Jq;
    @:overload(function (name: String): String{})
    public function attr(name: String, value: Dynamic): Jq;
    @:overload(function (name: String): String{})
    public function prop(name: String, value: Dynamic): Jq;
    public function removeAttr(name: String): Jq;
    public function on<A>(event: String, f: A->Void): Jq;
    public function empty(): Jq;
    public function addClass(name: String): Jq;
    public function removeClass(name: String): Jq;
    @:overload(function ():String{})
    public function val(value: String): Jq;
    public var length: Int;
    public function is(attribute: String):Bool;
    public function show(): Void;
    public function hide(): Void;
    public function index(target: Jq): Int;
    public function remove(): Void;
    public function insertBefore(target: Jq): Jq;
    public function parent(): Jq;
}

class JQuery{
    public static function j(selector): Jq{
        return untyped __js__("$(selector)");
    }

    public static function div(){ return j('<div></div>'); }
    public static function divNamed(name){ return j('<div></div>').attr("name", name); }

    public static function findAll(jq:Html, selector){
        var ret = jq.filter(selector);
        return if(ret.length == 0){
            jq.find(selector);
        }else{
            ret;
        }
    }

    public static function join(tag: String): Array<Jq> -> Jq{
        return function(htmls:Array<Jq>){
            return Lambda.fold(htmls,
                function(html:Jq, acc:Jq){ return acc.add(j(tag).append(html));}, 
                j('')
            );
        };
    }
    public static function gather(jq: Jq): Array<Jq> -> Jq{
        return function(htmls:Array<Jq>){
            return Lambda.fold(htmls,
                function(html:Jq, acc:Jq){ return acc.append(html);},
                jq
            );
        };
    }
    public static function add(tag: String, jq: Jq){
        return jq.add(j(tag));
    }

    public static function wrapBy(tag: String, jq: Jq){
        return j(tag).append(jq);
    }

    public static function fromArray(htmls: Array<Jq>): Jq{
        return Lambda.fold(htmls,
                function(html: Jq, acc: Jq){ return acc.add(html);},
                j(''));
    }

    public static inline function self(): Dynamic{
        return j(untyped __js__('this'));
    }
}
