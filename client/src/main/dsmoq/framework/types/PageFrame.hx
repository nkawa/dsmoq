package dsmoq.framework.types;

import js.html.Node;
import js.support.Stream;

/**
 * @author terurou
 */
typedef PageFrame<TPage: EnumValue> = {
    var navigation(default, null): Stream<PageNavigation<TPage>>;
    function render(page: PageContent<TPage>): Void;
}
