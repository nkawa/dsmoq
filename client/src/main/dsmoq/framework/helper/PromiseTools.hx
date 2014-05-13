package dsmoq.framework.helper;
import dsmoq.framework.types.Promise;
import js.jqhx.JqPromise;

/**
 * ...
 * @author terurou
 */
class PromiseTools {

    public static function toPromise(x: JqPromise): Promise<Dynamic> {
        return new Promise(function (resolve, reject) {
            x.then(resolve, reject);
        });
    }

}