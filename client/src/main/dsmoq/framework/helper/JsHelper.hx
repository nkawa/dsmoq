package dsmoq.framework.helper;

/**
 * ...
 * @author terurou
 */
class JsHelper {

    public static inline function encodeURI(x: String): String {
        return untyped __js__("encodeURI")(x);
    }

}