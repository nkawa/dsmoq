package dsmoq.framework.helper;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.Stream;
import js.html.Event;
import js.jqhx.JqHtml;

class JQueryTools {

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