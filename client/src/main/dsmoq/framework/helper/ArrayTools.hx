package dsmoq.framework.helper;

/**
 * ...
 * @author terurou
 */
class ArrayTools {

    public function filter<T>(iter: Iterable<T>, f: T -> Bool): Array<T> {
        var array = [];
        for (x in iter) {
            if (f(x)) array.push(x);
        }
        return array;
    }
}