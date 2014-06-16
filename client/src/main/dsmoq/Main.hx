package dsmoq;

import dsmoq.framework.ApplicationContext;
import dsmoq.framework.Engine;
import dsmoq.framework.types.Location;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageFrame;
import dsmoq.models.Profile;
import dsmoq.models.Service;
import dsmoq.Page;
import dsmoq.pages.DashboardPage;
import dsmoq.pages.DatasetEditPage;
import dsmoq.pages.DatasetListPage;
import dsmoq.pages.DatasetShowPage;
import dsmoq.pages.Frame;
import dsmoq.pages.GroupEditPage;
import dsmoq.pages.GroupListPage;
import dsmoq.pages.GroupShowPage;
import dsmoq.pages.ProfilePage;
import haxe.Resource;
import js.jqhx.JqHtml;
import js.jsviews.JsViews;
import js.support.JsTools;
import js.support.Option;
import js.support.PositiveInt;
import js.support.Promise;
import js.support.Unit;

using StringTools;
using js.support.OptionTools;
using dsmoq.framework.helper.JQueryTools;

/**
 * EntryPoint
 * @author terurou
 */
class Main {
    public static function main() {
        // TODO frameworkで管理すべき
        JsViews.views.helpers("isEmpty", function (x) {
            return (x == null) || (x.length <= 0);
        });

        JsViews.views.tags("debug", function (x) {
            trace(x);
            return "";
        });

        JsViews.views.tags("async", cast {
            dataBoundOnly: true,
			flow: true,
			autoBind: true,
            render: function(val) {
                var tagDef = JsViewsTools.tagDef();
                return switch (val) {
                    case Async.Pending:
                        "<img src='/resources/loading-large.gif'>";
                    case Async.Completed(x):
                        tagDef.tagCtx.render(x, false);
                };
            }
        });

        JsViews.views.tags("a", function (_) {
            var ctx = JsViewsTools.tagDef().tagCtx;
            var props = ctx.props;

            var buf = [];
            for (k in Reflect.fields(props)) {
                var v = Reflect.field(props, k);
                buf.push('${StringTools.urlEncode(k)}="${JsTools.encodeURI(v)}"');
            }
            return '<a data-history ${buf.join(" ")}>${ctx.render()}</a>';
        });

        JsViews.views.tags("img", function (_) {
            var ctx = JsViewsTools.tagDef().tagCtx;
            var props = ctx.props;

            var buf = [];
            for (k in Reflect.fields(props)) {
                var v = Reflect.field(props, k);
                buf.push('${StringTools.urlEncode(k)}="${JsTools.encodeURI(v)}"');
            }
            return '<img ${buf.join(" ")}>';
        });

        JsViews.views.tags("license", function (id) {
            var license = Lambda.find(Service.instance.licenses, function (x) return x.id == id);
            if (license == null) license = Service.instance.licenses[0];
            return '<span>${license.name}</span>';
        });


        JsViews.views.tags("datasize", function (size) {
            function round(x: Float) {
                return Math.fround(x * 10.0) / 10;
            }

            return if (Std.is(size, Float)) {
                if (size < 1024) {
                    size + "Byte";
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

        JsViews.views.converters("toInt", function (x) {
            return Std.parseInt(x);
        });

        var pagenationTemplate = JsViews.template(Resource.getString("share/pagination"));
        JsViews.views.tags("pagination", {
            dataBoundOnly: true,
            //init: function (tag, link) {
                //trace("init");
            //},
            //onBeforeLink: function() {
                //trace("onBeforeLink");
                //return true;
            //},
            render: function (_) {
                var tagDef = JsViewsTools.tagDef();
                var id: String = tagDef.tagCtx.props["id"];
                return '<div id="${id}" style="display:inline-block"></div>';
            },
            onAfterLink: function(tag, link) {
                var tagDef = JsViewsTools.tagDef();
                var root = tagDef.contents("*:first");
                tagDef.linkedElem = root; //linkedElemを設定しないとonUpdate()がコールされない

                var cls: String = tagDef.tagCtx.props["class"];
                var index: Int = tagDef.tagCtx.args[0];
                var pageDelta: Int = JsTools.orElse(tagDef.tagCtx.props["pagedelta"], 5);
                var pageDeltaCenter = Math.floor(pageDelta / 2);
                var pages: Int = tagDef.tagCtx.args[1];

                if (pages > 0) {
                    var range = [for (i in if (index < pageDeltaCenter) {
                                    0...((pageDelta < pages) ? pageDelta : pages);
                                } else if (index >= (pages - pageDeltaCenter)) {
                                    (pages - pageDelta)...(pages);
                                } else {
                                    var left = index - pageDeltaCenter;
                                    (left)...(left + pageDelta);
                                }) i];

                    root.html(pagenationTemplate.render({
                        cls: cls,
                        index: index,
                        range: range,
                        pages: pages
                    }));

                    root.find("ul").on("click", "a[data-value]", function (e) {
                        tagDef.update(new JqHtml(e.target).data("value"));
                        root.trigger("change.dsmoq.pagination");
                    });
                }
            },
            onUpdate: function(ev, eventArgs, tag) { // binding.onchange
                return false;
            },
            //onBeforeChange: function(ev, eventArgs) { //input.onchange
                //trace("onBeforeChange");
                //return true;
            //},
            //onDispose: function() {
                //trace("onDispose");
            //}
        });

        Engine.start(new Main());
    }

    public function new() : Void {
    }

    public function bootstrap(): Promise<Unit> {
        return Service.instance.bootstrap;
    }

    public function frame(context: ApplicationContext): PageFrame<Page> {
        return Frame.create(context);
    }

    public function content(page: Page): PageContent<Page> {
        return switch (page) {
            case Dashboard: DashboardPage.create();
            case DatasetList(page): DatasetListPage.create(page);
            case DatasetShow(id): DatasetShowPage.create(id);
            case DatasetEdit(id): DatasetEditPage.create(id);
            case GroupList(page): GroupListPage.create(page);
            case GroupShow(id): GroupShowPage.create(id);
            case GroupEdit(id): GroupEditPage.create(id);
            case Profile: ProfilePage.create();
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
                Some(DatasetList(parsePositiveInt(location.query["page"]).getOrDefault(1)));
            case ["datasets", id]:
                Some(DatasetShow(id));
            case ["datasets", id, "edit"]:
                Some(DatasetEdit(id));
            case ["groups"]:
                Some(GroupList(parsePositiveInt(location.query["page"]).getOrDefault(1)));
            case ["groups", id]:
                Some(GroupShow(id));
            case ["groups", id, "edit"]:
                Some(GroupEdit(id));
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
            case DatasetShow(id):
                { path: '/datasets/$id' };
            case DatasetEdit(id):
                { path: '/datasets/$id/edit' };

            case GroupList(page):
                { path: "/groups" };
            case GroupShow(id):
                { path: '/groups/$id' };
            case GroupEdit(id):
                { path: '/groups/$id/edit' };

            case Profile:
                { path: "/profile" };
        };
    }
}
