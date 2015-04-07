package dsmoq;

import conduitbox.Engine;
import conduitbox.LocationMapping;
import dsmoq.models.Profile;
import dsmoq.models.Service;
import dsmoq.models.TagDetail;
import dsmoq.Page;
import dsmoq.pages.*;
import haxe.Resource;
import haxe.Json;
import hxgnd.ArrayTools;
import hxgnd.Error;
import hxgnd.js.Html;
import hxgnd.js.JqHtml;
import hxgnd.js.JsTools;
import hxgnd.js.jsviews.JsViews;
import hxgnd.LangTools;
import hxgnd.PositiveInt;
import hxgnd.Promise;
import hxgnd.Unit;
import hxgnd.Option;

using StringTools;
using Lambda;
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
		
		JsViews.views.helpers("trimTags", function (x) {
			var r = ~/<[^<]+>/g;
            return r.replace(x, "").trim();
        });
		
		JsViews.views.helpers("trimScriptTags", function (x) {
			var r = ~/<script[^<]+<\/script>/g;
            return r.replace(x, "").trim();
        });
		
		JsViews.views.helpers("getTagColor", function(tag: Array<TagDetail>) {
			return function(key: String) {
				var target = tag.filter(function(x) { return x.tag == key; } );
				if (target.length == 0) {
					return "#777";
				}
				return target[0].color;
			};
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
                        "<img src='/resources/loading-large.gif' />";
                    case Async.Completed(x):
                        tagDef.tagCtx.render(x, true);
                };
            }
        });

        JsViews.views.tags("range", cast {
            baseTag: JsViews.getTag("for"),

            render: function (val) {
                var tagDef = JsViewsTools.tagDef();
                var tagCtx = tagDef.tagCtx;

                function toNum(x): Int {
                    return (Std.is(x, Int)) ? x : 0;
                }

                var start = toNum(tagCtx.props["start"]);
                var end = toNum(tagCtx.props["end"]);

                var v = if (start != end) {
                    if (tagCtx.args.empty()) {
                        ArrayTools.array(start...end);
                    } else if (Std.is(val, Array)) {
                        cast(val, Array<Dynamic>).slice(start, end);
                    } else {
                        val;
                    }
                } else {
                    val;
                }

                var render = untyped tagDef.baseTag.render;
                return untyped __js__("render.apply(this, v ? [v] : arguments)");
            },

            onArrayChange: function(ev, eventArgs) {
                JsViewsTools.tagDef().refresh();
            }
        });

        JsViews.views.tags("checkButton", {
            dataBoundOnly: true,
			flow: true,
			autoBind: true,
            render: function (_) {
                var tagDef = JsViewsTools.tagDef();
                var id: String = tagDef.tagCtx.props["id"];
                var cls: String = tagDef.tagCtx.props["class"];
                var type: String = LangTools.orElse(tagDef.tagCtx.props["type"], "submit");
                return '<button id="${id}" type="${type}" class="${cls} btn btn-xs btn-default unactive"><span class="glyphicon glyphicon-ok"></span></button>';
            },
            onAfterLink: function(tag, link) {
                var tagDef = JsViewsTools.tagDef();
                var root = tagDef.contents("*:first");
                tagDef.linkedElem = root; //linkedElemを設定しないとonUpdate()がコールされない

                var value: Bool = LangTools.orElse(tagDef.tagCtx.args[0], false);
                if (value) {
                    root.addClass("active");
                    root.addClass("btn-primary");
                    root.removeClass("unactive");
                    root.removeClass("btn-default");
                } else {
                    root.addClass("unactive");
                    root.addClass("btn-default");
                    root.removeClass("active");
                    root.removeClass("btn-primary");
                }

                root.on("click", function (_) {
                    tagDef.update(!LangTools.orElse(tagDef.tagCtx.args[0], false));
                });
            },
            onUpdate: function(ev, eventArgs, tag) { // binding.onchange
                var tagDef = JsViewsTools.tagDef();
                var root = tagDef.contents("*:first");
                var value: Bool = eventArgs.value;
                if (value) {
                    root.addClass("active");
                    root.addClass("btn-primary");
                    root.removeClass("unactive");
                    root.removeClass("btn-default");
                } else {
                    root.addClass("unactive");
                    root.addClass("btn-default");
                    root.removeClass("active");
                    root.removeClass("btn-primary");
                }
                tagDef.update(value);

                return true;
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
                                    ((pageDelta < pages) ? (pages - pageDelta) : 0)...(pages);
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
                toPage: function (location) {
                    var path = location.path.split("/");
                    path.shift();

                    function parsePositiveInt(x: String) {
                        var i = Std.parseInt(x);
                        return (i == null) ? None : Some(cast(i, PositiveInt));
                    }
					
					function getQuery(x: String) {
						return (x == null) ? None : Some(x);
					}
					
					function getFilters(x: String) {
						return (x == null) ? None : Some(Json.parse(x));
					}

                    return switch (path) {
                        case [""]:
                            Top;
                        case ["dashboard"]:
                            Dashboard;
							
                        case ["datasets"]:
                            DatasetList(
								parsePositiveInt(location.query["page"]).getOrDefault(1), 
								getQuery(location.query["query"]).getOrDefault(""),
								getFilters(location.query["filters"]).getOrDefault(new Array<{type: String, item: Dynamic}>())
							);
                        case ["datasets", id]:
                            DatasetShow(id);
                        case ["datasets", id, "edit"]:
                            DatasetEdit(id);
                        case ["groups"]:
                            GroupList(parsePositiveInt(location.query["page"]).getOrDefault(1), getQuery(location.query["query"]).getOrDefault(""));
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
                toLocation: function (page) {
                    return switch (page) {
                        case Top:
                            { path: "/" };
                        case Dashboard:
                            { path: "/dashboard" };
							
                        case DatasetList(page, query, filters):
                            var q = new Map();
                            q["page"] = Std.string(page);
							q["query"] = query;
							q["filters"] = Json.stringify(filters);
                            { path: "/datasets", query: q };
                        case DatasetShow(id):
                            { path: '/datasets/$id' };
                        case DatasetEdit(id):
                            { path: '/datasets/$id/edit' };

                        case GroupList(page, query):
							var q = new Map();
                            q["page"] = Std.string(page);
							q["query"] = query;
                            { path: "/groups", query: q };
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
					case Top: TopPage.render(slot, onClosed);
                    case Dashboard: DashboardPage.render(slot, onClosed);
                    case DatasetList(pageNum, query, filters): DatasetListPage.render(slot, onClosed, pageNum, query, filters);
                    case DatasetShow(id): DatasetShowPage.render(slot, onClosed, id);
                    case DatasetEdit(id): DatasetEditPage.render(slot, onClosed, id);
                    case GroupList(pageNum, query): GroupListPage.render(slot, onClosed, pageNum, query);
                    case GroupShow(id): GroupShowPage.render(slot, onClosed, id);
                    case GroupEdit(id): GroupEditPage.render(slot, onClosed, id);
                    case Profile: ProfilePage.render(slot, onClosed);
                }
            }
        });
    }
}
