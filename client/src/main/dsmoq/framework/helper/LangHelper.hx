package dsmoq.framework.helper;

import haxe.macro.Expr;
import dsmoq.framework.types.Error;
import dsmoq.framework.types.Result;
import haxe.macro.ExprTools;


/**
 * ...
 * @author terurou
 */
class LangHelper {

    public inline static function orElse<T>(a: Null<T>, b: T): T {
        return (a != null) ? a : b;
    }

}