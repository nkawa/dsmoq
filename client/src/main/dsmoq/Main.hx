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
import haxe.Json;
import haxe.macro.Compiler;
import haxe.macro.Context;
import haxe.macro.Expr;
import haxe.Resource;
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
import dsmoq.framework.helper.JsHelper;
using StringTools;

/**
 * EntryPoint
 * @author terurou
 */
class Main {
    public static function main() {
        JsViews.views.tags("a", function (_) {
            var ctx = JsViewsTools.tagCtx();
            var props = ctx.props;

            var buf = [];
            for (k in Reflect.fields(props)) {
                var v = Reflect.field(props, k);
                buf.push('${StringTools.urlEncode(k)}="${JsHelper.encodeURI(v)}"');
            }
            return '<a ${buf.join(" ")}>${ctx.render()}</a>';
        });

        JsViews.views.tags("datasize", function (size) {
            function round(x: Float) {
                return Math.fround(x * 10.0) / 10;
            }

            return if (Std.is(size, Float)) {
                if (size < 1024) {
                    size + "B";
                } else if (size < 1048576) {
                    round(size / 1024) + "KB";
                } else if (size < 1073741824) {
                    round(size / 1048576) + "MB";
                } else if (size < 1099511627776) {
                    round(size / 1073741824) + "GB";
                } else {
                    round(size / 1099511627776) + "TB";
                }
            } else {
                Std.string(size) + " is not number";
            }
        });

        JsViews.views.tags("pagination", {
            template: Resource.getString("share/panination"),
            dataBoundOnly: true,
            init: function (tag, link) {
                trace("init");
            },
            onBeforeLink: function() {
                trace("onBeforeLink");
                return true;
            },
            onAfterLink: function(tag, link) {
                trace("onAfterLink");
            },
            onUpdate: function(ev, eventArgs, tag) {
                trace("onUpdate");
                return true;
            },
            onBeforeChange: function(ev, eventArgs) {
                trace("onBeforeChange");
                return true;
            },
            onDispose: function() {
                trace("onDispose");
            }
        });

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

        var data = {
            profile: Service.instance.profile,
            id: "",
            password: "",
            location: url(LocationTools.currentLocation())
        };

        var ref = JsViews.objectObservable(data);

        var header = JQuery.find("#header");
        View.link("Header", header, data);

        context.location.then(function (location) {
            ref.setProperty("location", url(location));
        });

        Service.instance.then(function (event) {
            switch (event) {
                case SignedIn, SignedOut:
                    ref.setProperty("profile", Service.instance.profile);
            }
        });


        header.on("submit", "#signin-form", function (event: Event) {
            event.preventDefault();
            Service.instance.signin(data.id, data.password);
        });

        header.on("click", "#settings-button", function (event: Event) {
            event.preventDefault();
            trace(event);
        });

        header.on("click", "#signout-button", function (event: Event) {
            event.preventDefault();
            Service.instance.signout();
        });

        body.removeClass("loading");

        return PageHelper.toFrame({
            html: cast JQuery.find("#main")[0],
            navigation: new ControllableStream() //<a href="">をハンドルするようにしたので、無くてもよい？
        });
    }

    public function content(page: Page): PageContent<Page> {
        return switch (page) {
            case Dashboard:
                {
                    navigation: new ControllableStream(),
                    render: function (container: Element) {

                        View.getTemplate("DashBoard").link(container, {});
                    },
                    dispose: function () {
                    }
                }
            case DatasetList(page):
                {
                    navigation: new ControllableStream(),
                    render: function (container: Element) {
                        var x = { condition: { }, result: { } };
                        var binding = JsViews.objectObservable(x);
                        Service.instance.findDatasets().then(function (x) {
                            binding.setProperty("result", x);
                            View.getTemplate("dataset/list").link(container, binding.data());
                        }).thenError(function (e) trace(e));
                    },
                    dispose: function () {

                    }
                }
            case GroupList:
                {
                    navigation: new ControllableStream(),
                    render: function (container: Element) {
                        View.getTemplate("group/list").link(container, { } );
                    },
                    dispose: function () {

                    }
                }
        };
    }

    public function fromLocation(location: Location): Option<Page> {
        return switch (location.path) {
            case "/":
                Some(Dashboard);
            case "/datasets":
                var page = Std.parseInt(location.query["page"]);
                Some(DatasetList((page == null || page < 1) ? 1 : page));
            case "/groups":
                Some(GroupList);
            default:
                None;
        }
    }

    public function toLocation(page: Page): Location {
        return switch (page) {
            case Dashboard: { path: "/" };
            case DatasetList(page):
                var query = new Map();
                query["page"] = Std.string(page);
                { path: "/datasets", query: query };
            case GroupList: { path: "/groups" };
        };
    }
}

enum Page {
    Dashboard;
    DatasetList(page: UInt);
    GroupList;
}