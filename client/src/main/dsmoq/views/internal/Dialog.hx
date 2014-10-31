package dsmoq.views.internal;

import hxgnd.js.jsviews.JsViews;
import hxgnd.js.JQuery;
import haxe.Resource;

class Dialog {
    public static function showMessage(message: String, handler: Void -> Void): Void {
        var template = JsViews.template(Resource.getString("share/message_dialog"));
        var content = JQuery._(template.render({ message: message })).appendTo(JQuery._("body"));

        content.one("modal:destroyed", function (_) {
            content.remove();
            handler();
        });

        Modal.show(content);
    }

    public static function showConfirm(message: String, handler: Bool -> Void): Void {
        var template = JsViews.template(Resource.getString("share/confirm_dialog"));
        var content = JQuery._(template.render({ message: message })).appendTo(JQuery._("body"));

        function hide(result, event) {
            untyped content.modal("hide");
            content.one("modal:destroyed", function (_) {
                content.remove();
                handler(result);
            });
        }

        content.one("click", ".modal-dialog-ok", hide.bind(true));
        content.one("click", ".modal-dialog-cancel", hide.bind(false));

        Modal.show(content);
    }
}