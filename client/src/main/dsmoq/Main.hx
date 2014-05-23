package dsmoq;

import dsmoq.framework.ApplicationContext;
import dsmoq.framework.Engine;
import dsmoq.framework.types.PositiveInt;
import dsmoq.framework.View;
import js.support.ControllableStream;
import js.support.Promise;
import js.support.Stream;
import dsmoq.framework.types.Location;
import js.support.Option;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.types.PageFrame;
import js.support.Unit;
import dsmoq.framework.helper.PageHelper;
import dsmoq.models.HeaderModel;
import dsmoq.models.Service;
import haxe.ds.ObjectMap;
import haxe.Json;
import haxe.Resource;
import haxe.Timer;
import js.Browser;
import js.Error;
import js.html.AnchorElement;
import js.html.Element;
import js.html.Event;
import js.html.Node;
import js.jqhx.JQuery;
import js.jqhx.JqHtml;
import dsmoq.framework.LocationTools;
import js.jsviews.JsViews;
import js.support.JsTools;
using StringTools;
using js.support.OptionTools;
using dsmoq.framework.helper.JQueryTools;

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
                buf.push('${StringTools.urlEncode(k)}="${JsTools.encodeURI(v)}"');
            }
            return '<a ${buf.join(" ")}>${ctx.render()}</a>';
        });

        JsViews.views.tags("img", function (_) {
            var ctx = JsViewsTools.tagCtx();
            var props = ctx.props;

            var buf = [];
            for (k in Reflect.fields(props)) {
                var v = Reflect.field(props, k);
                buf.push('${StringTools.urlEncode(k)}="${JsTools.encodeURI(v)}"');
            }
            return '<img ${buf.join(" ")}>';
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
        var navigation = new ControllableStream();

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
                    navigation.update(PageNavigation.Reload);
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

        JQuery.find("#new-dataset-dialog-submit").on("click", function (event: Event) {
            event.preventDefault();
            Service.instance.createDataset(JQuery.find("#new-dataset-dialog form")).then(function (data) {
                untyped JQuery.find("#new-dataset-dialog").modal("hide");
                navigation.update(PageNavigation.Navigate(DatasetShow(data.id)));
            });
        });

        body.removeClass("loading");

        return PageHelper.toFrame({
            html: cast JQuery.find("#main")[0],
            navigation: navigation
        });
    }

    public function content(page: Page): PageContent<Page> {
        return switch (page) {
            case Dashboard:
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        View.getTemplate("DashBoard").link(container, {});
                    },
                    dispose: function () {
                    }
                }
            case DatasetList(page):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        var x = { condition: { }, result: { } };
                        var binding = JsViews.objectObservable(x);
                        Service.instance.findDatasets().then(function (x) {
                            binding.setProperty("result", x);
                            View.getTemplate("dataset/list").link(container, binding.data());
                        }, function (err) {
                            switch (err) {
                                case UnauthorizedError: trace("UnauthorizedError");
                                case _: trace("xxx");
                            }
                        });
                    },
                    dispose: function () {

                    }
                }
            case DatasetShow(id):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        Service.instance.getDataset(id).then(function (data) {
                            var binding = JsViews.objectObservable(data);
                            View.getTemplate("dataset/show").link(container, binding.data());

                            //JQueryTools.createEventStream(new JqHtml(container), "click").then(function (_) trace("c"));

                            function f(_) trace("one");
                            new JqHtml(container).one("click", f);
                            new JqHtml(container).unbind("click", f);

                            new JqHtml(container).find("#dataset-delete").on("click", function (_) {
                                Service.instance.deleteDeataset(id);
                            });
                        }, function (err) {
                            switch (err.name) {
                                case ErrorType.Unauthorized:
                                    container.innerHTML = "Permission denied";
                                    trace("UnauthorizedError");
                                case _:
                                    // TODO 通信エラーが発生しましたメッセージと手動リロードボタンを表示
                                    container.innerHTML = "network error";
                                    trace(err);
                            }
                        });
                    },
                    dispose: function () {

                    }
                }
            case DatasetEdit(id):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        container.innerHTML = 'dataset edit ${id}';
                    },
                    dispose: function () {

                    }
                }

            case GroupList(page):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        View.getTemplate("group/list").link(container, { } );
                    },
                    dispose: function () {

                    }
                }
            case GroupShow(id):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        container.innerHTML = 'group ${id}';
                    },
                    dispose: function () {

                    }
                }
            case GroupEdit(id):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        container.innerHTML = 'group edit ${id}';
                    },
                    dispose: function () {

                    }
                }

            case Profile:
                null;
        };
    }

    public function fromLocation(location: Location): Option<Page> {
        var path = location.path.split("/");
        path.shift();

        function parsePositiveInt(x: String) {
            var i = Std.parseInt(x);
            return (i == null) ? None : Some(cast(i, PositiveInt));
        }

        return switch (path) {
            case [""]:
                Some(Dashboard);
            case ["datasets"]:
                Some(DatasetList(parsePositiveInt(location.query["page"]).getOrElse(1)));
            case ["datasets", id]:
                Some(DatasetShow(id));
            case ["groups"]:
                Some(GroupList(parsePositiveInt(location.query["page"]).getOrElse(1)));
            case ["groups", id]:
                Some(GroupShow(id));
            case ["profile"]:
                Some(Profile);
            case _:
                None;
        }
    }

    public function toLocation(page: Page): Location {
        return switch (page) {
            case Dashboard:
                { path: "/" };

            case DatasetList(page):
                var query = new Map();
                query["page"] = Std.string(page);
                { path: "/datasets", query: query };
            case DatasetShow(id), DatasetEdit(id):
                { path: "/datasets/" + id };

            case GroupList(page):
                { path: "/groups" };
            case GroupShow(id), GroupEdit(id):
                { path: "/groups/" + id };

            case Profile:
                { path: "/profile" };
        };
    }
}

enum Page {
    Dashboard;

    //DatasetNew;
    DatasetList(page: PositiveInt);
    DatasetShow(id: String);
    DatasetEdit(id: String);

    //GroupNew;
    GroupList(page: PositiveInt);
    GroupShow(id: String);
    GroupEdit(id: String);

    Profile;
}