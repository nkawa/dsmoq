package dsmoq;

import dsmoq.framework.Engine;
import dsmoq.framework.Template;
import dsmoq.framework.types.Deferred;
import dsmoq.framework.types.DeferredStream;
import dsmoq.framework.types.Location;
import dsmoq.framework.types.Option;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.Unit;
import dsmoq.framework.helper.PageHelper;
import dsmoq.models.Model;
import js.Browser;
import js.html.Element;
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
        var bootstrap = new Deferred();
        var navigation = new DeferredStream();

        var root = Browser.document.documentElement;
        var body = JQuery.wrap(Browser.document.body);
        body.addClass("loading");

        JQuery.getJSON("/api/profile").done(function (x) {
            trace(x);

            var header = JQuery.find("#header");
            header.html(Template.render("Header"));
            header.on("", "", {}, function (e) {
            });

            //TODO イベントハンドラ設定

            body.removeClass("loading");
            bootstrap.resolve(Unit._);
        });

        return PageHelper.toFrame({
            html: cast JQuery.find("#main")[0],
            bootstrap: bootstrap.toPromise(),
            navigation: navigation.toPromiseStream()
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
        return null;
    }
}

enum Page {
    Dashboard;
}