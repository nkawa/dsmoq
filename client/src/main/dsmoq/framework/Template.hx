package dsmoq.framework;

import dsmoq.framework.types.Error;
import dsmoq.framework.types.Result;
import dsmoq.framework.types.Unit;
import js.html.Element;
import js.jsviews.JsViews;
import js.jsviews.JsViews.Template in JsTemplate;
import js.jqhx.JQuery;
import haxe.Resource;
using dsmoq.framework.helper.ResultTools;
using dsmoq.framework.helper.OptionHelper;

/**
 * ...
 * @author terurou
 */
class Template {
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

        target.toElement().appendChild;
        JsViews.getTemplate(name)
            .getOrThrow(new Error('undefined template: \'$name\''))
            .link(target, binding);
    }

    public static function render(name: String, ?data: {}): String {
        initialize();

        var template: {} -> String = Reflect.field(JsViews.render, name);
        if (template == null) throw new Error('undefined template: \'$name\'');
        return template(data);
    }

    public static function getTemplate(name: String) {
        initialize();
        return JsViews.getTemplate(name).getOrThrow(new Error('undefined template: \'$name\''));
    }

    public static function register(name: String, template: String): Void {
        initialize();
        JsViews.template(name, template);
    }
}