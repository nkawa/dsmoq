package dsmoq;

import dsmoq.framework.ApplicationContext;
import dsmoq.framework.Engine;
import dsmoq.framework.View;
import dsmoq.framework.types.ControllableStream;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.Stream;
import dsmoq.framework.types.Location;
import dsmoq.framework.types.Option;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.Unit;
import dsmoq.framework.helper.PageHelper;
import dsmoq.models.HeaderModel;
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
import dsmoq.framework.LocationTools;
import js.jsviews.JsViews;
using StringTools;

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

    public function bootstrap(): Promise<Unit> {
        return Service.instance.bootstrap;
    }

    // これ自体がViewModelだよね…
    public function frame(context: ApplicationContext): PageFrame<Page> {
        var body = JQuery.wrap(Browser.document.body);

        function url(location) {
            return "/oauth/signin_google?location=" + LocationTools.toUrl(location).urlEncode();
        }

        var data = { location: url(LocationTools.currentLocation()) };
        var ref = JsViews.observable(data);

        var header = JQuery.find("#header");
        View.link("Header", header, data);
        trace(data);


        context.location.then(function (location) {
            trace(LocationTools.toUrl(location));
            ref.setProperty("location", url(location));
        });






        header.on("submit", "#signin-form", function (event: Event) {
            event.preventDefault();
            trace(event);
            // ログインAPI呼び出し
        });

        //header.on("click", "#signin-with-google-button", function (event: Event) {
            //event.preventDefault();
            //trace(event);
            //// ログインAPI呼び出し
////"oauth/signin_google
////var location = StringTools.urlEncode(doc.location.pathname + doc.location.search + doc.location.hash);
////doc.location.href = url + "?location=" + location;
        //});

        header.on("click", "#settings-button", function (event: Event) {
            event.preventDefault();
            trace(event);
        });

        header.on("click", "#signout-button", function (event: Event) {
            event.preventDefault();
            trace(event);
        });

        //TODO jqueryイベントをPromiseStream変換

        // navigation.update(Navigate(Dashboard));



        //var data = js.jsviews.JsViews.observableObject(x.data);
        //Timer.delay(function () data.setProperty("isGuest", false), 300);

        //js.jsviews.JsViews.l(JQuery.find("body"));

        var d: Dynamic<Dynamic> = { };


        //header.on("", "", {}, function (e) {
        //});

        //TODO イベントハンドラ設定

        body.removeClass("loading");

        return PageHelper.toFrame({
            html: cast JQuery.find("#main")[0],
            navigation: new ControllableStream() //<a href="">をハンドルするようにしたので、無くてもよい？
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
            case DatasetList:
                {
                    html: function () {
                        var div = Browser.document.createElement("div");
                        div.innerHTML = "datasets";
                        return div ;
                    },
                    then: function (fn) {
                    },
                    dispose: function () {

                    }
                }
            case GroupList:
                {
                    html: function () {
                        var div = Browser.document.createElement("div");
                        div.innerHTML = "groups";
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
        return switch (location.path) {
            case "/": Some(Dashboard);
            case "/datasets": Some(DatasetList);
            case "/groups": Some(GroupList);
            default: None;
        }
    }

    public function toLocation(page: Page): Location {
        return switch (page) {
            case Dashboard: { path: "/" };
            case DatasetList: { path: "/datasets" };
            case GroupList: { path: "/groups" };
        };
    }
}

enum Page {
    Dashboard;
    DatasetList;
    GroupList;
}