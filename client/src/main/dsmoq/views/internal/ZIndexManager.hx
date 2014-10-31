package dsmoq.views.internal;

import hxgnd.js.Html;
import hxgnd.LangTools;

class ZIndexManager {
    inline static var DEFUALT_INDEX = 2000;
    inline static var INDEX_DELTA = 10;
    inline static var ZINDEX_KEY = "dsmoq-zindex";
    static var layers: Array<{fn: Void -> Void, chapter: Bool}> = [];

    public static function push(target: Html, onForcePop: Void -> Void): Void {
        var index: Int = target.data(ZINDEX_KEY);
        if (index == null) {
            index = layers.length;
            target.data(ZINDEX_KEY, index);
            layers.push({ fn: onForcePop, chapter: false });
            target.css("z-index", toZIndex(index));
        }
    }

    public static function pushWithBackdrop(front: Html, back: Html, onForcePop: Void -> Void): Void {
        var index: Int = front.data(ZINDEX_KEY);
        if (index == null) {
            index = layers.length;
            front.data(ZINDEX_KEY, index);
            layers.push({ fn: onForcePop, chapter: true });

            var zIndex = toZIndex(index);
            front.css("z-index", zIndex);
            back.css("z-index", zIndex - 1);
        }
    }

    public static function pop(target: Html): Void {
        var index: Int = target.data(ZINDEX_KEY);
        if (index != null) {
            if (layers[index].chapter) {
                layers.slice(index + 1).map(function (x) x.fn());
                layers = layers.slice(0, index);
            } else {
                layers[index] = { fn: LangTools.nop, chapter: false };
            }
            target.removeData(ZINDEX_KEY);
        }
    }

    inline static function toZIndex(index: Int) {
        return DEFUALT_INDEX + index * INDEX_DELTA;
    }
}