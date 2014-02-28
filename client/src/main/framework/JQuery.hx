package framework;

import framework.Types;

extern class Jq{
    public function append(jq: Jq):Jq;
    public function add(jq: Jq):Jq;
    public function find(selector: String):Jq;
    public function filter(selector: String):Jq;
    public function html(text: String):Jq;
    @:overload(function ():String{})
    public function text(text: String):Jq;
    @:overload(function (name: String): String{})
    public function attr(name: String, value: String): Jq;
    public function on<A>(event: String, f: A->Void): Jq;
    public function empty(): Jq;
    public function addClass(name: String): Jq;
    @:overload(function ():String{})
    public function val(value: String): Jq;
    public var length: Int;
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
}
