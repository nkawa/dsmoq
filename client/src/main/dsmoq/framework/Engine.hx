package dsmoq.framework;

import dsmoq.framework.types.Application;
import dsmoq.framework.types.Error;
import dsmoq.framework.types.Location;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.Option;
import js.Browser;
import js.html.Element;
import js.html.EventTarget;
import js.html.Node;
import js.html.PopStateEvent;

using Lambda;

/**
 * ...
 * @author terurou
 */
class Engine<TPage: EnumValue> {
    var app: Application<TPage>;
    var frame: PageFrame<TPage>;
    var content: PageContent<TPage>;
    var initialized: Bool;

    function new(app) {
        this.app = app;
        History.Adapter.bind(Browser.window, "statechange", onStateChange);
    }

    function run() {
        frame = app.frame();
        frame.bootstrap.then(function (_) onStateChange());
        frame.navigation.then(onPageEvent);
    }

    function onStateChange() {
        switch (app.fromLocation(LocationHelper.location())) {
            case Some(x): changePage(x);
            case None: throw new Error("cannot resolve page");
        }
    }

    function onPageEvent(event: PageNavigation<TPage>) {
        switch (event) {
            case Navigate(x): History.pushState("data", null, LocationHelper.toUrl(app.toLocation(x)));
        }
    }

    function changePage(page: TPage) {
        if (content != null) content.dispose();

        content = app.content(page);
        content.then(onPageEvent);
        frame.notify(content.html());
    }

    public static function start<TPage: EnumValue>(app: Application<TPage>): Void {
        new Engine(app).run();
    }
}

@:native("History")
private extern class History {
    static function pushState(data: Null<Dynamic>, title: Null<String>, url: String): Bool;
    static function getState(): Null<Dynamic>;

    static var Adapter: {
        function bind(element: EventTarget, name: String, handler: Void -> Void): Void;
    };
}

private class LocationHelper {
    public static function location(): Location {
        function toQueryMap(search: String) {
            var map = new Map();
            return map;
        }

        return {
            path: Browser.location.pathname,
            query: toQueryMap(Browser.location.search),
            hash: Browser.location.hash
        };
    }

    public static function toUrl<TPage: EnumValue>(location: Location): String {
        function toQuery(query: Null<Map<String, String>>) {
            var entries = [];
            if (query != null) {
                for (k in query.keys()) {
                    entries.push('${StringTools.htmlEscape(k)}=${StringTools.htmlEscape(query.get(k))}');
                }
            }
            return entries.empty() ? "" : '?${entries.join("&")}';
        }

        function toHash(hash: Null<String>) {
            return (hash == null || hash == "") ? "": '#$hash';
        }

        return '${location.path}${toQuery(location.query)}${toHash(location.hash)}';
    }
}