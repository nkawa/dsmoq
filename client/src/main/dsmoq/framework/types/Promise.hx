package dsmoq.framework.types;

import haxe.Timer;

/**
 * @author terurou
 */
class Promise<T> {
    @:allow(dsmoq.framework.types)
    var _state: _PromiseState<T>;
    @:allow(dsmoq.framework.types)
    var _resolvedHandlers: Array<T -> Void>;
    @:allow(dsmoq.framework.types)
    var _rejectedHandlers: Array<Dynamic -> Void>;

    public function new(executor: (T -> Void) -> (Dynamic -> Void) -> Void) {
        this._state = Pending;
        this._resolvedHandlers = [];
        this._rejectedHandlers = [];
        executor(resolve, reject);
    }

    public function state(): PromiseState {
        return switch (_state) {
            case Pending, Sealed: Pending;
            case Resolved(v): Resolved;
            case Rejected(e): Rejected;
        }
    }

    public function then(resolved: T -> Void, ?rejected: Dynamic -> Void): Promise<T> {
        switch (_state) {
            case Pending, Sealed:
                _resolvedHandlers.push(resolved);
                if (rejected != null) _rejectedHandlers.push(rejected);
            case Resolved(v):
                Timer.delay(_invokeResolved.bind(v), 0);
            case Rejected(e):
                Timer.delay(_invokeRejected.bind(e), 0);
        }
        return this;
    }

    public function thenError(rejected: Dynamic -> Void): Promise<T> {
        switch (_state) {
            case Pending, Sealed:
                _resolvedHandlers.push(rejected);
                if (rejected != null) _rejectedHandlers.push(rejected);
            case Resolved(v):
                // nop
            case Rejected(e):
                Timer.delay(_invokeRejected.bind(e), 0);
        }
        return this;
    }

    public function catchError(rejected: T -> Void): Promise<T> {
        switch (_state) {
            case Pending, Sealed:
                _rejectedHandlers.push(rejected);
            case Resolved(v):
            case Rejected(e):
                Timer.delay(_invokeRejected.bind(e), 0);
        }
        return this;
    }

    function resolve(x: T): Void {
        switch (_state) {
            case Pending:
                _state = Sealed;
                Timer.delay(_invokeResolved.bind(x), 0);
            default:
        }
    }

    function reject(x: Dynamic): Void {
        switch (_state) {
            case Pending:
                _state = Sealed;
                var error = (x == null) ? "rejected" : x;
                Timer.delay(_invokeRejected.bind(error), 0);
            default:
        }
    }

    @:allow(dsmoq.framework.types)
    function _invokeResolved(value: T): Void {
        try {
            for (f in _resolvedHandlers) f(value);
            _state = Resolved(value);
        } catch (e: Dynamic) {
            _invokeRejected(e);
        }
    }

    @:allow(dsmoq.framework.types)
    function _invokeRejected(error: Dynamic): Void {
        for (f in _rejectedHandlers)
            try f(error) catch (e: Dynamic) trace(e);
        _state = Rejected(error);
    }

    public static function resolved<T>(value: T): Promise<T> {
        return new Promise(function (resolve, _) {
            resolve(value);
        });
    }

    public static function rejected<T>(error: Dynamic): Promise<T> {
        return new Promise(function (_, reject) {
            reject(error);
        });
    }

    //public static function when<T>(iter: Iterable<Promise<T>>): Promise<T> {
//
//
        //return null;
    //}
}

enum PromiseState {
    Pending;
    Resolved;
    Rejected;
}

private enum _PromiseState<T> {
    Pending;
    Sealed;
    Resolved(value: T);
    Rejected(error: Dynamic);
}