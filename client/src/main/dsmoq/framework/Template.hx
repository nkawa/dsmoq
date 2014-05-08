package dsmoq.framework;

import dsmoq.framework.types.Error;
import dsmoq.framework.types.Result;
import dsmoq.framework.types.Unit;
import js.jsviews.JsViews;
import js.jsviews.JsViews.Template in JsTemplate;
import haxe.Resource;
using dsmoq.framework.helper.ResultTools;

/**
 * ...
 * @author terurou
 */
class Template {
    static var uninitialized = true;
    static function initialize() {
        if (uninitialized) {
            for (name in Resource.listNames()) {
                var regexp = ~/^template\/(.+)/;
                if (regexp.match(name)) {
                    var text = Resource.getString(name);
                    if (text != null) JsViews.templates(regexp.matched(1), text);
                }
            }
            uninitialized = false;
        }
    }

    public static function render(name: String, ?data: { } ): String {
        trace("***");
        //trace(js.jqueryhx.JQuery.create("<div/>"));

        JsViews.views.tags("cst", function (x) { trace(JsViewsTools.tagCtx()); return "<cst></cst>"; } );
        JsViews.views.converters("cnv", function (x) { trace(JsViewsTools.converterCtx()); return "";} );
        JsViews.views.helpers("help", function (x) { trace(JsViewsTools.args()); return "";} );

        JsViews.templates("{{cst}}<div>{{cnv/}}</div>{{/cst}}").render();
        JsViews.templates("{{cnv:'hoge' /}}").render();
        trace(JsViews.templates("{{>~help('hoge') /}}").render());

        JsViews.views.tags({ hoge: function() {} } );


        initialize();
        var template: {} -> String = Reflect.field(JsViews.render, name);
        if (template == null) throw new Error('undefined template: \'$name\'');
        return template(data);
    }

    public static function register(name: String, template: String): Void {
        initialize();
        JsViews.templates(name, template);
    }
}