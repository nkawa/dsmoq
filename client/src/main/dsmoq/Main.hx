package dsmoq;

import dsmoq.framework.ApplicationContext;
import dsmoq.framework.Engine;
import dsmoq.pages.DashboardPage;
import dsmoq.pages.DatasetEditPage;
import dsmoq.pages.DatasetListPage;
import dsmoq.pages.DatasetShowPage;
import js.support.PositiveInt;
import dsmoq.framework.View;
import dsmoq.models.DatasetGuestAccessLevel;
import dsmoq.models.GroupMember;
import dsmoq.models.GroupRole;
import dsmoq.models.Profile;
import dsmoq.pages.Frame;
import dsmoq.pages.ProfilePage;
import js.Boot;
import js.bootstrap.BootstrapButton;
import js.support.ControllablePromise;
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
import dsmoq.Page;
import js.jsviews.JsViews;
import js.support.JsTools;
import js.typeahead.Bloodhound;
import js.typeahead.Typeahead;
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
            case GroupList(page):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        var x = { condition: { }, result: { } };
                        var binding = JsViews.objectObservable(x);
                        Service.instance.findGroups().then(function (res) {
                            binding.setProperty("result", res);
                            View.getTemplate("group/list").link(container, binding.data());
                        });
                    },
                    dispose: function () {
                    }
                }
            case GroupShow(id):
                var navigation = new ControllableStream();
                {
                    navigation: navigation,
                    invalidate: function (container: Element) {
                        var root = new JqHtml(container);

                        Service.instance.getGroup(id).then(function (res) {
                            var data = {
                                myself: Service.instance.profile,
                                group: res,
                                members: Async.Pending,
                                datasets: Async.Pending,
                            };
                            var binding = JsViews.objectObservable(data);

                            View.getTemplate("group/show").link(root, data);

                            Service.instance.getGroupMembers(id).then(function (x) {
                                binding.setProperty("members", Async.Completed(x));
                            });

                            Service.instance.findDatasets({group: id}).then(function (x) {
                                binding.setProperty("datasets", Async.Completed(x));
                            });

                            root.find("#group-edit").on("click", function (_) {
                                navigation.update(PageNavigation.Navigate(GroupEdit(id)));
                            });

                            root.find("#group-delete").on("click", function (_) {
                                trace("delete");
                            });
                        });
                    },
                    dispose: function () {
                    }
                }
            case GroupEdit(id):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        var root = new JqHtml(container);

                        Service.instance.getGroup(id).then(function (res) {
                            var data = {
                                myself: Service.instance.profile,
                                group: res,
                                isMembersLoading: true,
                                members: {
                                    summary: { count: 0, offset: 0, total: 0 },
                                    results: []
                                },
                            };
                            var binding = JsViews.objectObservable(data);

                            View.getTemplate("group/edit").link(container, binding.data());

                            var engine = new Bloodhound<Profile>({
                                datumTokenizer: Bloodhound.tokenizers.obj.whitespace("name"),
                                queryTokenizer: Bloodhound.tokenizers.whitespace,
                                prefetch: {
                                    url: "/api/accounts",
                                    filter: function (x) {
                                        return x.data;
                                    }
                                }
                            });
                            engine.initialize();

                            Typeahead.initialize(root.find("#group-user-typeahead"), {}, {
                                source: engine.ttAdapter(),
                                displayKey: "name",
                                templates: {
                                    suggestion: function (x: Profile) {
                                        return '<p><img src="${x.image}/16"> ${x.name} <span class="text-muted">${x.fullname}, ${x.organization}</span></p>';
                                    },
                                    empty: null,
                                    footer: null,
                                    header: null
                                }
                            });

                            Service.instance.getGroupMembers(id).then(function (res) {
                                binding.setProperty("members.summary.count", res.summary.count);
                                binding.setProperty("members.summary.offset", res.summary.offset);
                                binding.setProperty("members.summary.total", res.summary.total);
                                JsViews.arrayObservable(data.members.results).refresh(res.results);
                                binding.setProperty("isMembersLoading", false);
                            });

                            root.find("#group-basics-submit").on("click", function (_) {
                                Service.instance.updateGroupBasics(id, data.group.name, data.group.description);
                            });

                            root.find("#group-icon-form").on("change", "input[type=file]", function (e) {
                                if (new JqHtml(e.target).val() != "") {
                                    root.find("#group-icon-submit").show();
                                } else {
                                    root.find("#group-icon-submit").hide();
                                }
                            });
                            root.find("#group-icon-submit").on("click", function (_) {
                                Service.instance.changeGroupImage(id, JQuery.find("#group-icon-form")).then(function (res) {
                                    var img = res.images.filter(function (x) return x.id == res.primaryImage)[0];
                                    binding.setProperty("group.primaryImage.id", img.id);
                                    binding.setProperty("group.primaryImage.url", img.url);
                                    root.find("#group-icon-form input[type=file]").val("");
                                    root.find("#group-icon-submit").hide();
                                });
                            });

                            root.find("#group-user-add").on("click", function (_) {
                                var name = Typeahead.getVal(root.find("#group-user-typeahead"));
                                engine.get(name, function (res) {
                                    if (res.length == 1) {
                                        var item: GroupMember = {
                                            id: res[0].id,
                                            name: res[0].name,
                                            fullname: res[0].fullname,
                                            organization: res[0].organization,
                                            title: res[0].title,
                                            image: res[0].image,
                                            role: dsmoq.models.GroupRole.Member
                                        };
                                        JsViews.arrayObservable(data.members.results).insert(item);
                                    }
                                    Typeahead.setVal(root.find("#group-user-typeahead"), "");
                                });
                            });
                            root.find("#group-member-submit").on("click", function (_) {
                                Service.instance.updateGroupMemberRoles(id, data.members.results);
                            });
                        });
                    },
                    dispose: function () {
                    }
                }

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
