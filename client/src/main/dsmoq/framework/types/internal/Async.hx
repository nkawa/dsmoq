package dsmoq.framework.types.internal;

/**
 * ...
 * @author terurou
 */
@:allow(dsmoq.framework.types)
class Async {

    public static function dispatch(f: Void -> Void): Void {
        haxe.Timer.delay(f, 0);
    }

}