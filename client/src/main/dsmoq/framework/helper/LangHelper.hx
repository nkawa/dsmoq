package dsmoq.framework.helper;

import js.Error;

class LangHelper {

    public inline static function orElse<T>(a: Null<T>, b: T): T {
        return (a != null) ? a : b;
    }

}