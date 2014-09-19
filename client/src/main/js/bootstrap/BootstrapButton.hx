package js.bootstrap;

import hxgnd.js.JqHtml;

class BootstrapButton {
    public static inline function set(html: JqHtml, state: String): JqHtml {
        return untyped html.button(state);
    }

    public static inline function reset(html: JqHtml): JqHtml {
        return untyped html.button("reset");
    }

    public static inline function setLoading(html: JqHtml) :JqHtml {
        return untyped html.button("loading");
    }

    public static inline function toggle(html: JqHtml): JqHtml {
        return untyped html.button("toggle");
    }
}
