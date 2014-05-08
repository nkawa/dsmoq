package dsmoq.framework.types;

import js.html.Node;

/**
 * ...
 * @author terurou
 */
typedef PageContent<TPage: EnumValue> = {
    function html(): Node;
    function then(handler: PageNavigation<TPage> -> Void): Void;
    function dispose(): Void;
}
