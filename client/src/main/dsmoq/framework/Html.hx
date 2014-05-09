package dsmoq.framework;

import js.html.Element;
import js.jqhx.JqHtml;
import js.jqhx.JQuery;

abstract Html(Element) {
    inline function new(x: Element) {
        this = x;
    }

    @:from public static inline function fromElement(x: Element): Html {
        return new Html(x);
    }

    @:to public inline function toElement(): Element {
        return this;
    }

    @:from public static inline function fromJqHtml(x: JqHtml): Html {
        return new Html(x[0]);
    }

    @:to public inline function toJqHtml(): JqHtml {
        return new JqHtml(this);
    }
}