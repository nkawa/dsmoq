package dsmoq;

import hxgnd.Error;
import hxgnd.js.Html;
import hxgnd.Unit;
import hxgnd.Option;
import conduitbox.Application.LocationMapping;
import conduitbox.Engine;
import conduitbox.ApplicationContext;
import hxgnd.Promise;
import hxgnd.js.JsTools;
import hxgnd.LangTools;
import dsmoq.pages.*;


import dsmoq.models.Profile;
import dsmoq.models.Service;
import dsmoq.Page;
import haxe.Resource;
import hxgnd.js.JqHtml;
import hxgnd.js.jsviews.JsViews;
import hxgnd.js.JsTools;
import hxgnd.PositiveInt;

using StringTools;
using hxgnd.OptionTools;

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
                        tagDef.tagCtx.render(x, true);
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
            return '<a data-navigation ${buf.join(" ")}>${ctx.render()}</a>';
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
                var pageDelta: Int = LangTools.orElse(tagDef.tagCtx.props["pagedelta"], 5);
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






        Engine.start({
            locationMapping: LocationMapping.Mapping({
                from: function (location) {
                    var path = location.path.split("/");
                    path.shift();

                    function parsePositiveInt(x: String) {
                        var i = Std.parseInt(x);
                        return (i == null) ? None : Some(cast(i, PositiveInt));
                    }

                    return switch (path) {
                        case [""]:
                            Dashboard;
                        case ["datasets"]:
                            DatasetList(parsePositiveInt(location.query["page"]).getOrDefault(1));
                        case ["datasets", id]:
                            DatasetShow(id);
                        case ["datasets", id, "edit"]:
                            DatasetEdit(id);
                        case ["groups"]:
                            GroupList(parsePositiveInt(location.query["page"]).getOrDefault(1));
                        case ["groups", id]:
                            GroupShow(id);
                        case ["groups", id, "edit"]:
                            GroupEdit(id);
                        case ["profile"]:
                            Profile;
                        case _:
                            throw new Error("invalid path");
                    }
                },
                to: function (page) {
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
            }),

            bootstrap: function () {
                return Service.instance.bootstrap;
            },

            createFrame: Frame.create,

            renderPage: function (page: Page, slot: Html, onClosed: Promise<Unit>) {
                return switch (page) {
                    case Dashboard: DashboardPage.render(slot, onClosed);
                    case DatasetList(pageNum): DatasetListPage.render(slot, onClosed, pageNum);
                    case DatasetShow(id): DatasetShowPage.render(slot, onClosed, id);
                    case DatasetEdit(id): DatasetEditPage.render(slot, onClosed, id);
                    case GroupList(pageNum): GroupListPage.render(slot, onClosed, pageNum);
                    case GroupShow(id): GroupShowPage.render(slot, onClosed, id);
                    case GroupEdit(id): GroupEditPage.render(slot, onClosed, id);
                    case Profile: ProfilePage.render(slot, onClosed);
                }
            }
        });
    }
}
