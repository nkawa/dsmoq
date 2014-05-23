package js.support;

class ControllableStream<A> extends Stream<A> {
    public function new(?canceled: Void -> Void) {
        super(function (_, _, _) {
            return (canceled == null) ? function() {} : canceled;
        });
    }

    override public function update(value: A): Void {
        super.update(value);
    }

    override public function close(): Void {
        super.close();
    }

    override public function fail(x: Dynamic): Void {
        super.fail(x);
    }
}