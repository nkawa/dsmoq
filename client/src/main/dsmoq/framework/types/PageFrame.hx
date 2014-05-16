package dsmoq.framework.types;

import js.html.Node;

/**
 * @author terurou
 */
typedef PageFrame<TPage: EnumValue> = {
    var bootstrap(default, null): Promise<Unit>;
    var navigation(default, null): Stream<PageNavigation<TPage>>;

    function notify(message: Node): Void;
}
