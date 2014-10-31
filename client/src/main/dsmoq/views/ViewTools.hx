package dsmoq.views;

import hxgnd.js.Html;
import hxgnd.js.JqHtml;
import hxgnd.js.JQuery;
import hxgnd.js.JsTools;
import hxgnd.LangTools;
import hxgnd.Option;
import hxgnd.Promise;
import hxgnd.Unit;
import hxgnd.js.jsviews.JsViews;
import dsmoq.views.internal.Dialog;
import dsmoq.views.internal.Loading;
import dsmoq.views.internal.Modal;

using hxgnd.OptionTools;

class ViewTools {
    /**
     * ローディングマスクを表示します。
     * @param target マスキング対象
     * @param ?cancelable 画面操作によりキャンセル可能な場合、true。省略した場合はfalseとなる。
     */
    public static function showLoading(target: Html, ?cancelable = false): Promise<Bool> {
        return Loading.show(target, cancelable);
    }

    /**
     * ローディングマスクの表示を解除します。
     * @param target マスキング対象
     */
    public static function hideLoading(target: Html): Void {
        Loading.hide(target, false);
    }

    /**
     * メッセージダイアログを表示します。
     * @param message 表示メッセージ
     */
    public static function showMessage(message: String): Promise<Unit> {
        return new Promise(function (ctx) {
            Dialog.showMessage(message, ctx.fulfill.bind(Unit._));
        });
    }

    /**
     * 確認ダイアログを表示します。
     * @param message 表示メッセージ
     */
    public static function showConfirm(message: String): Promise<Bool> {
        return new Promise(function (ctx) {
            Dialog.showConfirm(message, ctx.fulfill);
        });
    }

    /**
     * モーダルダイアログを表示します。
     * @param template モーダルダイアログのHTML Element
     * @param constructor　モーダルダイアログ表示時の初期化処理
     */
    public static function showModal<T>(
            template: Template, ?data: {},  constructor: JqHtml -> PromiseContext<T> -> Void): Promise<T> {
        var root = JQuery._("<div></div>").appendTo("body");
        template.link(root, data);
        var target = root.find(".modal:first");

        return new Promise(function (ctx: PromiseContext<T>) {
            var wrappedCtx = {
                fulfill: function (x) {
                    Modal.hide(target, false);
                    ctx.fulfill(x);
                },
                reject: function (error) {
                    Modal.hide(target, false);
                    ctx.reject(error);
                },
                cancel: function () {
                    Modal.hide(target, false);
                    ctx.cancel();
                },
                onCancel: LangTools.nop
            };

            target.one("modal:destroyed", function onHide(_) {
                root.remove();
                if (wrappedCtx != null) wrappedCtx.onCancel();
                ctx.cancel();
            });

            constructor(target, wrappedCtx);
            Modal.show(target);
        });
    }

    /**
     * モーダルダイアログの表示を解除します。
     * @param target モーダルダイアログのHTML Element
     */
    public static function hideModal(target: Html): Void {
        Modal.hide(target, true);
    }
}
