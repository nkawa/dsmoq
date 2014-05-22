package dsmoq.framework.types;

import js.html.Element;

/**
 * ...
 * @author terurou
 */
typedef PageContent<TPage: EnumValue> = {
    var navigation(default, null): Stream<PageNavigation<TPage>>;
    function invalidate(container: Element): Void;
    function dispose(): Void;
}
