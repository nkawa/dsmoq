package framework;

import framework.Types;

extern class Jq{
    public function append(jq: Jq):Jq;
    public function add(jq: Jq):Jq;
    public function find(selector: String):Jq;
    public function html(text: String):Jq;
    public function text(text: String):Jq;
    public function attr(name: String, value: String): Jq;
    public function on<A>(event: String, f: A->Void): Jq;
    public function empty(): Jq;
    public function addClass(name: String): Jq;
    public function val(): String;
}

class JQuery{
    public static function j(selector): Jq{
        return untyped __js__("$(selector)");
    }

    public static function div(){ return j('<div></div>'); }
    public static function divNamed(name){ return j('<div></div>').attr("name", name); }

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
