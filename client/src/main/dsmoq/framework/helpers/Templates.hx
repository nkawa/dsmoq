package dsmoq.framework.helpers;

import dsmoq.framework.helpers.*;
import haxe.Resource;
import dsmoq.framework.Types;
import promhx.Stream.Stream;
using Lambda;
using dsmoq.framework.JQuery;

class Templates{
    public static function create<Input>(resourceName):Component<Input, Input, Void>{
        function render(x:Dynamic){
            var body = JQuery.j(Resource.getString(resourceName));
            var fields = Reflect.fields(x).map(function(fieldName){
                var attribute = 'data-$fieldName';
                var value:Dynamic = Reflect.field(x, fieldName);
                if(untyped __js__('value === true')){
                    body.findAll('[${attribute}="show:false"]').hide();
                    return "";
                }else if(untyped __js__('value === false')){
                    body.findAll('[${attribute}="show:true"]').hide();
                    return "";
                }else{
                    var jq = body.findAll('[$attribute]');
                    if(jq.length == 0){
                        return "";
                    }else{
                        assignMap.get(jq.attr(attribute))(jq, value);
                        return fieldName;
                    }
                }
            }).filter(function(x){return x != "";});
            function state(){
                var ret = untyped {};
                fields.iter(function(fieldName){
                    var attribute = 'data-$fieldName';
                    var jq = body.findAll('[$attribute]');
                    var value = extractMap.get(jq.attr(attribute))(jq);
                    Reflect.setField(ret, fieldName, value);
                });
                return ret;
            }
            return {
                html: body,
                state: state,
                event: new Stream()
            };
        }
        return Components.toComponent(render);
    }

    static private var assignMap = {
        var map = new Map<String, Html -> String -> Void>();
        map.set("text", assignText);
        map.set("textfield", assignTextField);
        map.set("attribute:src", assignAttributeSrc);
        map;
    };

    static private var extractMap = {
        var map = new Map<String, Html -> String>();
        map.set("text", extractText);
        map.set("textfield", extractTextField);
        map.set("attribute:src", extractAttributeSrc);
        map;
    };
    private static function assignText     (jq: Html, v: String){ jq.text(v); }
    private static function assignTextField(jq: Html, v: String){ jq.val(v); }
    private static function assignAttributeSrc(jq: Html, v: String){ jq.attr("src", v); }

    private static function extractText     (jq: Html){ return jq.text(); }
    private static function extractTextField(jq: Html){ return jq.val(); }
    private static function extractAttributeSrc(jq: Html){ return jq.attr("src"); }
}
