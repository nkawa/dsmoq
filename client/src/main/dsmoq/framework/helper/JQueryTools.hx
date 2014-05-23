package dsmoq.framework.helper;

import js.support.Promise;
import js.support.Stream;
import js.html.Event;
import js.jqhx.JqHtml;
import js.jqhx.JqPromise;

class JQueryTools {

    public static function toPromise(x: JqPromise): Promise<Dynamic> {
        return new Promise(function (resolve, reject) {
            x.then(resolve, reject);
            return function () { };
        });
    }

    public static function createEventPromise(jq: JqHtml, eventName: String, ?selector: String): Promise<Event> {
        return new Promise(function (resolve, reject) {
            jq.one(eventName, selector, resolve);
            return function cancelEventStream() jq.unbind(eventName, resolve);
        });
    }

    public static function createEventStream(jq: JqHtml, eventName: String, ?selector: String): Stream<Event> {
        return new Stream(function (update, close, fail) {
            jq.bind(eventName, selector, update);
            return function cancelEventStream() jq.unbind(eventName, update);
        });
    }

}