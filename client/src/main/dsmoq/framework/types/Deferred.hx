package dsmoq.framework.types;

/**
 * @author terurou
 */
class Deferred<T> {
    var resolvedFlag: Bool;
    var rejectedFlag: Bool;
    var resolvedValue: T;
    var rejectedValue: Dynamic;
    var handlers: Array<T -> Void>;
    var errorHandlers: Array<Dynamic -> Void>;

    public function new() {
        this.resolvedFlag = false;
        this.rejectedFlag = false;
        this.handlers = [];
        this.errorHandlers = [];
    }

    public function isResolved(): Bool {
        return resolvedFlag;
    }

    public function isRejected(): Bool {
        return rejectedFlag;
    }

    public function resolve(x: T): Void {
        if (!resolvedFlag && !rejectedFlag) {
            try {
                for (f in handlers) f(x);
                resolvedValue = x;
                resolvedFlag = true;
            } catch (e: Dynamic) {
                reject(e);
            }
        } else {
            throw new Error("");
        }
    }

    public function reject(?x: Dynamic): Void {
        if (!resolvedFlag && !rejectedFlag) {
            if (x == null) x = "rejected";
            for (f in errorHandlers) {
                try f(x) catch (e: Dynamic) trace(e);
            }
            rejectedValue = x;
            rejectedFlag = true;
        }
    }

    public function then(x: T -> Void): Deferred<T> {
        if (resolvedFlag) {
            x(resolvedValue);
        } else if (!rejectedFlag) {
            handlers.push(x);
        }
        return this;
    }

    public function thenError(x: T -> Void): Deferred<T> {
        if (rejectedFlag) {
            x(rejectedValue);
        } else if (!resolvedFlag) {
            errorHandlers.push(x);
        }
        return this;
    }

    public function toPromise(): Promise<T> {
        return this; //TODO
    }

    public static function resolved<T>(x: T): Deferred<T> {
        var deferred = new Deferred();
        deferred.resolve(x);
        return deferred;
    }

    public static function rejected<T>(x: Dynamic): Deferred<T> {
        var deferred = new Deferred();
        deferred.reject(x);
        return deferred;
    }

    public static function when<T>(iter: Iterable<Promise<T>>) : Promise<T> {
        return null;
    }
}