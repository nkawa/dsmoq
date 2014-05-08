package dsmoq.framework.types;

/**
 * @author terurou
 */

class DeferredStream<T> {
    public function new() {
    }

    public function then(handler: T-> Void): Void {

    }

    public function toPromiseStream(): PromiseStream<T> {
        return this; //todo
    }
}