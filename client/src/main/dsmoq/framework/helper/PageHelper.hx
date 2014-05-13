package dsmoq.framework.helper;

import dsmoq.framework.types.DeferredStream;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.PromiseStream;
import dsmoq.framework.types.Unit;
import js.html.Element;
import js.html.Node;

using dsmoq.framework.helper.LangHelper;

/**
 * ...
 * @author terurou
 */
class PageHelper {

    public static function toFrame<TPage: EnumValue>(
        data: { html: Element, ?bootstrap: Promise<Unit>, ?navigation: PromiseStream<PageNavigation<TPage>> }
    ): PageFrame<TPage> {
        var html = data.html;
        var bootstrap = data.bootstrap.orElse(Promise.resolved(Unit._));
        var navigation = data.navigation.orElse(new DeferredStream().toPromiseStream());

        return {
            bootstrap: bootstrap,
            navigation: navigation,
            notify: function notify (pageHtml: Node) {
                html.innerHTML = "";
                html.appendChild(pageHtml);
            }
        };
    }

    //public static function toFrameAsync<TPage: EnumValue>(promise: Promise<Element>): PageFrame<TPage> {

    //}

}
