package dsmoq.views.internal;

import hxgnd.js.Html;
import hxgnd.js.JsTools;

class Modal {
    public static function show(target: Html): Void {
        target.one("hide", function onHide(_) {
            ZIndexManager.pop(target);
        });
        target.one("destroyed", function (_) {
            JsTools.setImmediate(function () {
                target.trigger("modal:destroyed");
            });
        });

        untyped target.modal({ show: true, backdrop: "static", keyboard: false });

        ZIndexManager.pushWithBackdrop(
            target.parent(".modal-scrollable"),
            target.parent(".modal-scrollable").siblings(".modal-backdrop:last"),
            Modal.hide.bind(target, true));
    }

    public static function hide(target: Html, canTrigger: Bool): Void {
        untyped target.modal("hide");
        ZIndexManager.pop(target);
        if (canTrigger) target.trigger("hide");
    }
}