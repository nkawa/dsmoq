package js.support;

class ArrayTools {
    public static function groupBy<A>(array: Array<A>, f: A -> String): Dynamic<Array<A>> {
        var result: Dynamic<Array<A>> = { };
        for (x in array) {
            var key = f(x);
            if (Reflect.hasField(result, key)) {
                Reflect.field(result, key).push(x);
            } else {
                Reflect.setField(result, key, [x]);
            }
        }
        return result;
    }
}