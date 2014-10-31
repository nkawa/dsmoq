package dsmoq.views.internal;

import hxgnd.Error;
import hxgnd.js.Html;
import hxgnd.js.JqHtml;
import hxgnd.js.JQuery;
import hxgnd.js.JsTools;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import haxe.Resource;

class Loading {
    public static function show(target: Html, ?cancelable = false): Promise<Bool> {
        if (target.length > 0) {
            var tpl = JsViews.template(Resource.getString("share/loading"));

            var container = target.parent();
            container.append(tpl.render({ cancelable: cancelable }));

            var front = container.children(".loading-mask:last");
            var back = container.children(".loading-mask-backdrop:last");
            ZIndexManager.pushWithBackdrop(front, back, hide.bind(target, false));

            function resize() {
                var width = target.outerWidth();
                var height = target.outerHeight();
                var offset = target.offset();
                front.offset({
                    left: offset.left + (width - front.outerWidth()) / 2,
                    top: offset.top + (height - front.outerHeight()) / 2
                });
                back.width(width).height(height).offset(offset);
            }

            var loading = JQuery._([front[0], back[0]]);
            untyped loading.__resize = resize;
            untyped target[0].__loading = loading;
            LayoutUpdateManager.add(resize);

            if (cancelable) {
                front.on("click", ".loading-mask-cancel", function (_) {
                    hide(target, true);
                });
            }

            resize();

            return new Promise(function (ctx: PromiseContext<Bool>) {
                front.one("dsmoq:destroy", function (_, canceled) {
                    ctx.fulfill(canceled);
                });
            });
        } else {
            throw new Error("target is not found");
        }
    }

    public static function hide(target: Html, canceled: Bool): Void {
        if (target.length > 0) {
            var loading: JqHtml = untyped target[0].__loading;
            if (loading != null) {
                ZIndexManager.pop(loading);
                LayoutUpdateManager.remove(untyped loading.__resize);
                untyped __js__("delete loading.__resize");
                untyped __js__("delete target[0].loading");
                loading.hide();
                JQuery._(loading[0]).trigger("dsmoq:destroy", canceled);
                JsTools.setImmediate(function () loading.remove());
            }
        } else {
            throw new Error("target is not found");
        }
    }
}


private class LayoutUpdateManager {
    static var handlers = [];

    public static function __init__() {
        JQuery._("body").on("dsmoq:layoutUpdated", function (_) {
            for (x in handlers) x();
        });
    }

    public static function add(fn: Void -> Void): Void {
        handlers.push(fn);
    }

    public static function remove(fn: Void -> Void): Void {
        handlers.remove(fn);
    }
}