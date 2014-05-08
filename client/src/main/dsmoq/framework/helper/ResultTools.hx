package dsmoq.framework.helper;
import dsmoq.framework.types.Error;
import dsmoq.framework.types.Result;

/**
 * ...
 * @author terurou
 */
class ResultTools {

    public static inline function create<T>(f: Void -> T): Result<T> {
        return try {
            Success(f());
        } catch (e: Error) {
            Failure(e);
        } catch (e: Dynamic) {
            Failure(new Error(Std.string(e)));
        }
    }

    public static inline function map<A, B>(x: Result<A>, f: A -> B): Result<B> {
        return switch (x) {
            case Success(a):
                try {
                    Success(f(a));
                } catch (e: Error) {
                    Failure(e);
                } catch (e: Dynamic) {
                    Failure(new Error(Std.string(e)));
                }
            case Failure(e):
                Failure(e);
        }
    }


}