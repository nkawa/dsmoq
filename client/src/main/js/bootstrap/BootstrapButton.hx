package js.bootstrap;

import hxgnd.js.Html;
import hxgnd.js.JqHtml;

class BootstrapButton {
    public static inline function set(html: Html, state: String): JqHtml {
        return untyped html.button(state);
    }

    public static inline function reset(html: Html): JqHtml {
        return untyped html.button("reset");
    }

    public static inline function setLoading(html: Html) :JqHtml {
        return untyped html.button("loading");
    }

    public static inline function toggle(html: Html): JqHtml {
        return untyped html.button("toggle");
    }
}
