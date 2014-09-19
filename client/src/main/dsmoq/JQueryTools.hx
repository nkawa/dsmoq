package dsmoq;

import hxgnd.Promise;
import hxgnd.Stream;
import js.html.Event;
import hxgnd.js.JqHtml;
import hxgnd.js.JqPromise;

class JQueryTools {

    public static function toPromise(x: JqPromise): Promise<Dynamic> {
        return new Promise(function (context: PromiseContext<Dynamic>) {
            x.then(context.fulfill, context.reject);
            return function () { };
        });
    }

    public static function createEventPromise(jq: JqHtml, eventName: String, ?selector: String): Promise<Event> {
        return new Promise(function (context: PromiseContext<Event>) {
            jq.one(eventName, selector, context.fulfill);
            return function cancelEventStream() jq.off(eventName, context.fulfill);
        });
    }

    public static function createEventStream(jq: JqHtml, eventName: String, ?selector: String): Stream<Event> {
        return new Stream(function (context: StreamContext<Event>) {
            jq.on(eventName, selector, context.update);
            return function cancelEventStream() jq.off(eventName, context.update);
        });
    }

}