package dsmoq.framework.helper;

import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.PageNavigation;
import js.support.Promise;
import js.support.Stream;
import js.support.Unit;
import js.html.Element;
import js.html.Node;

using dsmoq.framework.helper.LangHelper;

/**
 * ...
 * @author terurou
 */
class PageHelper {

    public static function toFrame<TPage: EnumValue>(
        data: { html: Element, ?bootstrap: Promise<Unit>, ?navigation: Stream<PageNavigation<TPage>> }
    ): PageFrame<TPage> {
        var html = data.html;
        var bootstrap = data.bootstrap.orElse(Promise.resolved(Unit._));
        var navigation = data.navigation.orElse(new Stream(function (notify, _, reject) {
            return function () { };
        }));

        return {
            navigation: navigation,
            render: function render(page: PageContent<TPage>) {
                html.innerHTML = "";
                page.invalidate(html);
            }
        };
    }

    //public static function toFrameAsync<TPage: EnumValue>(promise: Promise<Element>): PageFrame<TPage> {

    //}

}









