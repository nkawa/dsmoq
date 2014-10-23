package dsmoq;

import js.Error;
import hxgnd.Result;
import hxgnd.Unit;
import js.html.Element;
import hxgnd.js.jsviews.JsViews;
import hxgnd.js.jsviews.JsViews.Template in JsTemplate;
import hxgnd.js.JQuery;
import haxe.Resource;
import hxgnd.js.Html;

using hxgnd.ResultTools;
using hxgnd.OptionTools;

class View {
    static var uninitialized = true;
    static inline function initialize(): Void {
        if (uninitialized) {
            registerResources();
            uninitialized = false;
        }
    }
    static function registerResources(): Void {
        for (name in Resource.listNames()) {
            var regexp = ~/^template\/(.+)/;
            if (regexp.match(name)) {
                var text = Resource.getString(name);
                if (text != null) JsViews.template(regexp.matched(1), text);
            }
        }
    }

    public static function link(name: String, target: Html, ?binding: {}): Void {
        initialize();

        var tpl = JsViews.getTemplate(name);
        if (tpl == null) throw new Error('undefined template: \'$name\'');
        tpl.link(target, binding);
    }

    //public static function render(name: String, ?data: {}): String {
        //initialize();
//
        //var template: {} -> String = Reflect.field(JsViews.render, name);
        //if (template == null) throw new Error('undefined template: \'$name\'');
        //return template(data);
    //}

    public static function getTemplate(name: String) {
        initialize();
        var tpl = JsViews.getTemplate(name);
        if (tpl == null) throw new Error('undefined template: \'$name\'');
        return tpl;
    }

    public static function register(name: String, template: String): Void {
        initialize();
        JsViews.template(name, template);
    }
}