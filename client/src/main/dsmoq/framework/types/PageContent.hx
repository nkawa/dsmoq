package dsmoq.framework.types;

import js.html.Element;

/**
 * ...
 * @author terurou
 */
typedef PageContent<TPage: EnumValue> = {
    function render(container: Element): Void;
    function then(handler: PageNavigation<TPage> -> Void): Void;
    function dispose(): Void;
}
