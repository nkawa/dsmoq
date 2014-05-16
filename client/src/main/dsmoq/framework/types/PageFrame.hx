package dsmoq.framework.types;

import js.html.Node;

/**
 * @author terurou
 */
typedef PageFrame<TPage: EnumValue> = {
    var navigation(default, null): Stream<PageNavigation<TPage>>;
    function notify(message: Node): Void;
}
