package dsmoq;

import dsmoq.framework.Engine;
import dsmoq.framework.Template;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.Stream;
import dsmoq.framework.types.Location;
import dsmoq.framework.types.Option;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.Unit;
import dsmoq.framework.helper.PageHelper;
import dsmoq.models.Service;
import haxe.macro.Compiler;
import haxe.macro.Context;
import haxe.macro.Expr;
import haxe.Timer;
import js.Browser;
import js.html.AnchorElement;
import js.html.Element;
import js.html.Event;
import js.html.Node;
import js.jqhx.JQuery;
import js.jqhx.JqHtml;

/**
 * EntryPoint
 * @author terurou
 */
class Main {
    public static function main() {
        Engine.start(new Main());
    }

    public function new() : Void {
    }

    public function frame(): PageFrame<Page> {
        var navigation = new Stream(function (update, _, _) {


            return function () {};
        });

        var body = JQuery.wrap(Browser.document.body);
        body.addClass("loading");

        //var i = foo(1, 2, 3);




        Service.instance.bootstrap.map(function (x) {
            return "boot";
        }).then(function (x) trace(x));

        //Service.event;

        var bootstrap = new Promise(function (resolve, reject) {
            var xhr = JQuery.getJSON("/api/profile");
            xhr.done(function (x) {
                var header = JQuery.find("#header");
                Template.link("Header", header, x.data);

                header.on("submit", "#signin-form", function (event: Event) {
                    event.preventDefault();
                    trace(event);
                    // ログインAPI呼び出し
                });

                header.on("click", "#signin-with-google-button", function (event: Event) {
                    event.preventDefault();
                    trace(event);
                    // ログインAPI呼び出し
                });

                header.on("click", "#settings-button", function (event: Event) {
                    event.preventDefault();
                    trace(event);
                });

                header.on("click", "#signout-button", function (event: Event) {
                    event.preventDefault();
                    trace(event);
                });

                //TODO jqueryイベントをPromiseStream変換



                trace(x.data);

                var data = js.jsviews.JsViews.observableObject(x.data);
                Timer.delay(function () data.setProperty("isGuest", false), 300);

                //js.jsviews.JsViews.l(JQuery.find("body"));

                var d: Dynamic<Dynamic> = { };


                //header.on("", "", {}, function (e) {
                //});

                //TODO イベントハンドラ設定

                body.removeClass("loading");
                resolve(Unit._);
            });
            return xhr.abort;
        });

        return PageHelper.toFrame({
            html: cast JQuery.find("#main")[0],
            bootstrap: bootstrap,
            navigation: navigation
        });
    }

    public function content(page: Page): PageContent<Page> {
        return switch (page) {
            case Dashboard:
                var div = Browser.document.createElement("div");
                div.innerHTML = "test";

                //div.innerHTML = "<div style='width: 10px; height: 2000px; background-color: #eee;'></div>";

                //div.innerHTML = "<img src='/resources/loading-large.gif'>";
                {
                    html: function () {
                        return div;
                    },
                    then: function (fn) {
                    },
                    dispose: function () {

                    }
                }


        };
    }

    public function fromLocation(location: Location): Option<Page> {
        return Some(Dashboard);
    }

    public function toLocation(page: Page): Location {
        return switch (page) {
            case Dashboard: {path: "/"};
        };
    }
}

enum Page {
    Dashboard;
}