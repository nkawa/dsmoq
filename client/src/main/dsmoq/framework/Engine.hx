package dsmoq.framework;

import dsmoq.framework.types.Application;
import dsmoq.framework.types.ControllablePromise;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.types.Stream;
import dsmoq.framework.types.Unit;
import dsmoq.framework.types.Option;
import js.Browser;
import js.Error;
import js.html.AnchorElement;
import js.html.Element;
import js.html.Event;
import js.html.EventTarget;

using Lambda;
using dsmoq.framework.helper.OptionHelper;


class Engine<TPage: EnumValue> {
    var app: Application<TPage>;
    var frame: PageFrame<TPage>;
    var content: PageContent<TPage>;

    // TODO 要リファクタリング
    function new(app) {
        this.app = app;
    }

    function run() {
        app.bootstrap().then(function onStartup(_) {
            var inited = new ControllablePromise();

            var location = new Stream(function (update, _, _) {
                inited.then(function (_) {
                    History.Adapter.bind(Browser.window, "statechange", function () {
                        update(LocationTools.toLocation(Browser.location));
                    });
                });
                return function () {};
            })
            .then(function onLocationUpdated(location) {
                switch (app.fromLocation(location)) {
                    case Some(x): changePage(x);
                    case None: throw new Error("cannot resolve page");
                }
            }, function (err) {
                untyped __js__("console.error(err)");
            });

            var context = { location: location };

            Browser.document.addEventListener("click", function (event: Event) {
                function getAnchor(elem: Element) {
                    return if (elem == null || elem.tagName == null) {
                        None;
                    } else if (elem.tagName.toUpperCase() == "A") {
                        Some(cast(elem, AnchorElement));
                    } else {
                        getAnchor(elem.parentElement);
                    }
                }

                getAnchor(cast event.target).each(function (a: AnchorElement) {
                    if (!~/^javascript:/.match(StringTools.trim(a.href))
                            && a.host == Browser.location.host && a.protocol == Browser.location.protocol) {
                        event.preventDefault();
                        History.pushState(null, null, a.href);
                    }
                });
            }, false);

            this.frame = app.frame(context);
            frame.navigation.then(function (navi) {
                trace(navi);
                switch (navi) {
                    case Navigate(page):
                        History.pushState(null, null, LocationTools.toUrl(app.toLocation(page)));
                }
            }, function (e) {
                trace("TODO error handling");
                trace(e);
            });

            switch (app.fromLocation(LocationTools.toLocation(Browser.location))) {
                case Some(x): changePage(x);
                case None: throw new Error("cannot resolve page");
            }

            inited.resolve(Unit._);
        }, function (e) {
            untyped __js__("console.error(arguments[0])");
        });
    }

    function onPageEvent(event: PageNavigation<TPage>) {
        switch (event) {
            case Navigate(x): History.pushState("data", null, LocationTools.toUrl(app.toLocation(x)));
        }
    }

    function changePage(page: TPage) {
        if (content != null) content.dispose();

        content = app.content(page);
        content.navigation.then(onPageEvent);
        frame.render(content);
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