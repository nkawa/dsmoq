package dsmoq;

import dsmoq.framework.ApplicationContext;
import dsmoq.framework.Engine;
import dsmoq.pages.DashboardPage;
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
                var tag = JsViewsTools.tag();
                return switch (val) {
                    case Async.Pending:
                        "<img src='/resources/loading-large.gif'>";
                    case Async.Completed(x):
                        tag.tagCtx.render(x);
                };
            }
        });

        JsViews.views.tags("a", function (_) {
            var ctx = JsViewsTools.tag().tagCtx;
            var props = ctx.props;

            var buf = [];
            for (k in Reflect.fields(props)) {
                var v = Reflect.field(props, k);
                buf.push('${StringTools.urlEncode(k)}="${JsTools.encodeURI(v)}"');
            }
            return '<a data-history ${buf.join(" ")}>${ctx.render()}</a>';
        });

        JsViews.views.tags("img", function (_) {
            var ctx = JsViewsTools.tag().tagCtx;
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
            //    trace("init");
            //},
            //onBeforeLink: function() {
                //trace("onBeforeLink");
                //return true;
            //},
            render: function (val) {
                var tag = JsViewsTools.tag();

                var id: String = tag.tagCtx.props["id"];
                var cls: String = tag.tagCtx.props["class"];
                var pageSize: Int = tag.tagCtx.props["pagesize"];
                var pageDelta: Int = tag.tagCtx.props["pagedelta"];
                var pageDeltaCenter = Math.floor(pageDelta / 2);
                var total: Int = tag.tagCtx.args[0].total;
                var count: Int = tag.tagCtx.args[0].count;
                var offset: Int = tag.tagCtx.args[0].offset;
                var page = Math.ceil(offset / pageSize) + 1;
                var last = Math.ceil(total / pageSize);

                var range = [for (i in if (page <= pageDeltaCenter) {
                    1...((pageDelta < last) ? pageDelta + 1 : last + 1);
                } else if (page >= (last - pageDeltaCenter)) {
                    (last - pageDelta + 1)...(last+1);
                } else {
                    var left = page - pageDeltaCenter;
                    (left)...(left + pageDelta);
                }) i];

                return pagenationTemplate.render({ id: id, cls: cls, page: page, range: range, last: last });
            },
            onAfterLink: function(tag, link) {
                var tag = JsViewsTools.tag();
                var root = tag.contents("*:first");
                root.on("click", "a[data-value]", function (e) {
                    root.attr("data-value", new JqHtml(e.target).attr("data-value"));
                    root.trigger("change.dsmoq.pagination");
                });
            },
            onUpdate: function(ev, eventArgs, tag) { // binding.onchange
                trace("onUpdate");
                return true;
            },
            onBeforeChange: function(ev, eventArgs) { //input.onchange
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

    public function frame(context: ApplicationContext): PageFrame<Page> {
        return Frame.create(context);
    }

    public function content(page: Page): PageContent<Page> {
        return switch (page) {
            case Dashboard: DashboardPage.create();
            case DatasetList(page):
                {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        // TODO ページング処理
                        var x = { condition: { }, result: { } };
                        var binding = JsViews.objectObservable(x);
                        Service.instance.findDatasets().then(function (x) {
                            binding.setProperty("result", x);
                            View.getTemplate("dataset/list").link(container, binding.data());
                        }, function (err) {
                            // TODO
                        });
                    },
                    dispose: function () {
                    }
                }
            case DatasetShow(id):
                var navigation = new ControllableStream();
                {
                    navigation: navigation,
                    invalidate: function (container: Element) {
                        Service.instance.getDataset(id).then(function (data) {
                            trace(data);

                            var binding = JsViews.objectObservable(data);
                            View.getTemplate("dataset/show").link(container, binding.data());

                            new JqHtml(container).find("#dataset-edit").on("click", function (_) {
                                navigation.update(PageNavigation.Navigate(DatasetEdit(id)));
                            });

                            new JqHtml(container).find("#dataset-delete").createEventStream("click").chain(function (_) {
                                // TODO ダイアログ
                                return if (Browser.window.confirm("ok?")) {
                                    Promise.resolved(Unit._);
                                } else {
                                    Promise.rejected(Unit._);
                                }
                            }, function (_) return None).chain(function (_) {
                                return Service.instance.deleteDeataset(id);
                            }).then(function (_) {
                                // TODO 削除対象データセット閲覧履歴（このページ）をHistoryから消す
                                navigation.update(PageNavigation.Navigate(DatasetList(1)));
                            });
                        }, function (err) {
                            switch (err.name) {
                                case ServiceErrorType.Unauthorized:
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
                var navigation = new ControllableStream();
                {
                    navigation: navigation,
                    invalidate: function (container: Element) {
                        Service.instance.getDataset(id).then(function (x) {
                            var root = new JqHtml(container);

                            // TODO clone
                            var data = {
                                myself: Service.instance.profile,
                                licenses: Service.instance.licenses,
                                dataset: x,
                            };

                            trace(data);

                            var binding = JsViews.objectObservable(data);
                            View.getTemplate("dataset/edit").link(root, data);

                            var attrbuteEngine = new Bloodhound({
                                datumTokenizer: Bloodhound.tokenizers.obj.whitespace("value"),
                                queryTokenizer: Bloodhound.tokenizers.whitespace,
                                remote: {
                                    url: "/api/suggests/attributes",
                                    replace: function (url, query) {
                                        return '$url?query=$query';
                                    },
                                    filter: function (x: {status: String, data: Array<String>}) {
                                        return (x.status == "OK") ? x.data.map(function (x) return {value: x}) : [];
                                    }
                                }
                            });
                            attrbuteEngine.initialize();

                            var ownerEngine = new Bloodhound({
                                datumTokenizer: Bloodhound.tokenizers.obj.whitespace("name"),
                                queryTokenizer: Bloodhound.tokenizers.whitespace,
                                remote: {
                                    url: "/api/suggests/users_and_groups",
                                    replace: function (url, query) {
                                        return '$url?query=$query';
                                    },
                                    filter: function (x: {status: String, data: Array<Dynamic>}) {
                                        return (x.status == "OK") ? x.data : [];
                                    }
                                }
                            });
                            ownerEngine.initialize();

                            function setAttributeTypeahead() {
                                Typeahead.initialize(root.find(".attribute-typeahead"), {
                                    source: attrbuteEngine.ttAdapter(),
                                });
                            }
                            function removeAttributeTypeahead() {
                                Typeahead.destroy(root.find(".attribute-typeahead"));
                            }

                            setAttributeTypeahead();

                            Typeahead.initialize(root.find("#dataset-owner-typeahead"), {}, {
                                source: ownerEngine.ttAdapter(),
                                displayKey: "name",
                                templates: {
                                    suggestion: function (x) {
                                        return '<p>${x.name}</p>';
                                    },
                                    empty: null,
                                    footer: null,
                                    header: null
                                }
                            });
                            root.find("#dataset-owner-add").on("click", function (_) {
                                var name = Typeahead.getVal(root.find("#dataset-owner-typeahead"));
                                Service.instance.getOwner(name).then(function (owner) {
                                    var ownerships = JsViews.arrayObservable(data.dataset.ownerships);
                                    ownerships.insert({
                                        id: owner.id,
                                        name: owner.name,
                                        fullname: owner.fullname,
                                        organization: owner.organization,
                                        image: owner.image,
                                        ownerType: owner.dataType,
                                        accessLevel: 1,
                                    });
                                });
                            });

                            root.find("#dataset-ownership-submit").on("click", function (_) {
                                Service.instance.updateDatasetACL(id, data.dataset.ownerships.map(function (x) {
                                    return {
                                        id: x.id,
                                        type: x.ownerType,
                                        accessLevel: x.accessLevel
                                    }
                                }));
                            });



                            root.find("#dataset-attribute-add").on("click", function (_) {
                                removeAttributeTypeahead();
                                var attrs = JsViews.arrayObservable(data.dataset.meta.attributes);
                                attrs.insert({ name: "", value:"" });
                                setAttributeTypeahead();
                            });

                            root.on("click", ".dataset-attribute-remove", function (e) {
                                removeAttributeTypeahead();
                                var index = new JqHtml(e.target).data("value");
                                var attrs = JsViews.arrayObservable(data.dataset.meta.attributes);
                                attrs.remove(index);
                                setAttributeTypeahead();
                            });

                            root.find("#dataset-basics-submit").on("click", function (_) {
                                Service.instance.updateDatasetMetadata(id, data.dataset.meta);
                            });

                            root.find("#dataset-file-add-form").on("change", "input[type=file]", function (e) {
                                if (new JqHtml(e.target).val() != "") {
                                    root.find("#dataset-file-add-submit").show();
                                } else {
                                    root.find("#dataset-file-add-submit").hide();
                                }
                            });
                            root.find("#dataset-file-add-submit").on("click", function (_) {
                                Service.instance.addDatasetFiles(id, root.find("#dataset-file-add-form")).then(function (res) {
                                    root.find("#dataset-file-add-submit").hide();
                                    JsViews.arrayObservable(data.dataset.files).insert(res[0]);
                                });
                            });
                            root.on("click", ".dataset-file-edit-start", function (e) {
                                var fid: String = new JqHtml(e.target).data("value");
                                var file = data.dataset.files.filter(function (x) return x.id == fid)[0];
                                var d = { name: file.name, description: file.description };

                                var target = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-edit");
                                var menu = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-menu");

                                var tpl = JsViews.template(Resource.getString("share/dataset/file/edit"));
                                tpl.link(target, d);
                                menu.hide();

                                function close() {
                                    target.empty();
                                    menu.show();
                                    tpl.unlink(target);
                                    target.off();
                                }

                                target.on("click", ".dataset-file-edit-submit", function (_) {
                                    Service.instance.updateDatatetFileMetadata(id, fid, d.name, d.description).then(function (res) {
                                        var fb = JsViews.objectObservable(file);
                                        fb.setProperty("name", res.name);
                                        fb.setProperty("description", res.description);
                                        fb.setProperty("url", res.url);
                                        fb.setProperty("size", res.size);
                                        fb.setProperty("createdAt", res.createdAt);
                                        fb.setProperty("createdBy", res.createdBy);
                                        fb.setProperty("updatedAt", res.updatedAt);
                                        fb.setProperty("updatedBy", res.updatedBy);
                                        close();
                                    });
                                });

                                target.on("click", ".dataset-file-edit-cancel", function (_) {
                                    close();
                                });
                            });
                            root.on("click", ".dataset-file-replace-start", function (e) {
                                var fid: String = new JqHtml(e.target).data("value");
                                var file = data.dataset.files.filter(function (x) return x.id == fid)[0];

                                var target = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-replace");
                                var menu = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-menu");

                                target.html(Resource.getString("share/dataset/file/replace"));
                                menu.hide();

                                function close() {
                                    target.empty();
                                    menu.show();
                                    target.off();
                                }

                                target.find("input[type=file]").on("change", function (e) {
                                    if (new JqHtml(e.target).val() != "") {
                                        target.find(".dataset-file-replace-submit").attr("disabled", false);
                                    } else {
                                        target.find(".dataset-file-replace-submit").attr("disabled", true);
                                    }
                                });

                                target.on("click", ".dataset-file-replace-submit", function (_) {
                                    Service.instance.replaceDatasetFile(id, fid, target.find("form")).then(function (res) {
                                        var fb = JsViews.objectObservable(file);
                                        fb.setProperty("name", res.name);
                                        fb.setProperty("description", res.description);
                                        fb.setProperty("url", res.url);
                                        fb.setProperty("size", res.size);
                                        fb.setProperty("createdAt", res.createdAt);
                                        fb.setProperty("createdBy", res.createdBy);
                                        fb.setProperty("updatedAt", res.updatedAt);
                                        fb.setProperty("updatedBy", res.updatedBy);
                                        close();
                                    });
                                });

                                target.on("click", ".dataset-file-replace-cancel", function (_) {
                                    close();
                                });
                            });
                            root.on("click", ".dataset-file-delete", function (e) {
                                var fid: String = new JqHtml(e.target).data("value");
                                JsTools.confirm("can delete?").bind(function (_) {
                                    return Service.instance.removeDatasetFile(id, fid);
                                }).then(function (_) {
                                    var files = data.dataset.files.filter(function (x) return x.id != fid);
                                    JsViews.arrayObservable(data.dataset.files).refresh(files);
                                });
                            });

                            root.find("#dataset-icon-form").on("change", "input[type=file]", function (e) {
                                if (new JqHtml(e.target).val() != "") {
                                    root.find("#dataset-icon-submit").show();
                                } else {
                                    root.find("#dataset-icon-submit").hide();
                                }
                            });
                            root.find("#dataset-icon-submit").on("click", function (_) {
                                Service.instance.changeDatasetImage(id, JQuery.find("#dataset-icon-form")).then(function (res) {
                                    var img = res.images.filter(function (x) return x.id == res.primaryImage)[0];
                                    binding.setProperty("dataset.primaryImage.id", img.id);
                                    binding.setProperty("dataset.primaryImage.url", img.url);
                                    root.find("#dataset-icon-form input[type=file]").val("");
                                    root.find("#dataset-icon-submit").hide();
                                });
                            });

                            root.find("#dataset-finish-editing").on("click", function (_) {
                                navigation.update(PageNavigation.Back); //TODO ヒストリーを消す
                            });

                            root.find("#dataset-guest-access-submit").on("click", function (_) {
                                Service.instance.setDatasetGuestAccessLevel(id, data.dataset.defaultAccessLevel);
                            });
                        });
                    },
                    dispose: function () {
                    }
                }

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
