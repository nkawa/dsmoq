package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.models.GroupMember;
import dsmoq.models.GroupRole;
import dsmoq.models.Service;
import dsmoq.models.User;
import dsmoq.View;
import dsmoq.views.ViewTools;
import haxe.ds.Option;
import haxe.Resource;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.JsTools;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Unit;
import js.bootstrap.BootstrapButton;
import js.html.Event;
import js.html.EventTarget;

using hxgnd.OptionTools;

class GroupEditPage {
    inline static var MemberCandidateSize = 5;

    public static function render(root: Html, onClose: Promise<Unit>, id: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var rootData = { data: Async.Pending };
        var rootBinding = JsViews.observable(rootData);
        View.getTemplate("group/edit").link(root, rootData);

        Service.instance.getGroup(id).flatMap(function (x) {
            return switch (x.role) {
                case GroupRole.Manager:
                    Promise.fulfilled(x);
                case _:
                    Promise.rejected(new ServiceError("", ServiceErrorType.Unauthorized));
            }
        }).thenError(function (err) {
            root.html(switch (err.name) {
                case ServiceErrorType.NotFound: "Not found";
                case ServiceErrorType.Unauthorized: "Permission denied";
                default: "Network error";
            });
        }).then(function (res) {
            var data = {
                myself: Service.instance.profile,
                group: res,
                groupErrors: {description: ""},
                members: Async.Pending,
            };
            var binding = JsViews.observable(data);
            rootBinding.setProperty("data", Async.Completed(data));

            function loadGroupMember() {
                Service.instance.getGroupMembers(id).then(function (x) {
                    binding.setProperty("members", Async.Completed({
                        index: Math.ceil(x.summary.offset / 20),
                        total: x.summary.total,
                        items: x.results,
                        pages: Math.ceil(x.summary.total / 20)
                    }));
                });
            }

            loadGroupMember();

            // basics tab ------------------------
            root.find("#group-basics-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#group-basics-submit"));
                root.find("#group-basics").find("input,textarea").attr("disabled", true);
                Service.instance.updateGroupBasics(id, data.group.name, data.group.description).then(function (_) {
                    Notification.show("success", "save successful");
                }, function (err) {
                    switch (err.name) {
                        case ServiceErrorType.BadRequest:
                            for (x in cast(err, ServiceError).detail) {
                                binding.setProperty('groupErrors.${x.name}', x.message);
                            }
                    }
                    Notification.show("error", "error happened");
                }, function () {
                    BootstrapButton.reset(root.find("#group-basics-submit"));
                    root.find("#group-basics").find("input,textarea").removeAttr("disabled");
                });
            });

            // icon tab -------------------------
            root.find("#group-icon-form").on("change", "input[type=file]", function (e) {
                if (JQuery._(e.target).val() != "") {
                    root.find("#group-icon-submit").show();
                } else {
                    root.find("#group-icon-submit").hide();
                }
            });
            root.find("#group-icon-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#group-icon-submit"));
                Service.instance.changeGroupImage(id, JQuery._("#group-icon-form")).then(function (res) {
                    var img = res.images.filter(function (x) return x.id == res.primaryImage)[0];
                    binding.setProperty("group.primaryImage.id", img.id);
                    binding.setProperty("group.primaryImage.url", img.url);
                    root.find("#group-icon-form input[type=file]").val("");
                    root.find("#group-icon-submit").hide();
                    Notification.show("success", "save successful");
                }, function (err) {
                    Notification.show("error", "error happened");
                }, function () {
                    BootstrapButton.reset(root.find("#group-icon-submit"));
                    root.find("#group-icon input").removeAttr("disabled");
                });
                root.find("#group-icon input").attr("disabled", true);
            });

            // members tab ----------------------
            root.find("#group-members").on("click", "#add-member-menu-item", function (_) {
                showAddMemberDialog().then(function (members) {
                    ViewTools.showLoading("body");
                    Service.instance.addGroupMember(id, members).then(function (x) {
                        ViewTools.hideLoading("body");
                        loadGroupMember();
                        Notification.show("success", "save successful");
                    }, function (err) {
                        ViewTools.hideLoading("body");
                        Notification.show("error", "error happened");
                    });
                });
            });

            function getMemberByElement(target: EventTarget): Option<dsmoq.models.GroupMember> {
                var node = JQuery._(target);
                var index = node.parents("tr[data-index]").data("index");
                return switch (data.members) {
                    case Async.Completed(members):
                        OptionTools.toOption(members.items[index]);
                    case _:
                        Option.None;
                }
            }

            root.find("#group-members").on("change", ".dsmoq-role-select", function (e) {
                // bindingが更新タイミングの問題があるため、setImmediateを挟む
                JsTools.setImmediate(function () {
                    getMemberByElement(e.currentTarget).iter(function (member) {
                        Service.instance.updateGroupMemberRole(id, member.id, member.role).then(function (_) {
                            Notification.show("success", "save successful");
                        }, function (e) {
                            Notification.show("error", "error happened");
                        });
                    });
                });
            });

            root.find("#group-members").on("click", ".dsmoq-remove-button", function (e) {
                getMemberByElement(e.currentTarget).iter(function (member) {
                    ViewTools.showConfirm("Are you sure you want to remove?").then(function (isOk) {
                        if (isOk) {
                            ViewTools.showLoading("body");
                            Service.instance.removeGroupMember(id, member.id)
                                .flatMap(function (_) return Service.instance.getGroupMembers(id))
                                .then(function (x) {
                                    loadGroupMember();
                                    ViewTools.hideLoading("body");
                                    Notification.show("success", "save successful");
                                }, function (err) {
                                    ViewTools.hideLoading("body");
                                    Notification.show("error", "error happened");
                                });
                        }
                    });
                });
            });

            // ----------------------------------
            root.find("#group-finish-editing").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(Page.GroupShow(id)));
            });
        });

        return navigation.promise;
    }

    static function showAddMemberDialog() {
        var data = {
            query: "",
            offset: 0,
            hasPrev: false,
            hasNext: false,
            items: new Array<{selected: Bool, item: User}>(),
            selectedIds: new Array<String>(),
            isManager: false
        }
        var binding = JsViews.observable(data);
        var tpl = JsViews.template(Resource.getString("template/group/add_member_dialog"));

        return ViewTools.showModal(tpl, data, function (html, ctx) {
            function searchMemberCandidate(?query: String, offset = 0) {
                var limit = MemberCandidateSize + 1;
                Service.instance.findUsers({ query: query, offset: offset, limit: limit }).then(function (users) {
                    var list = users.slice(0, MemberCandidateSize)
                                    .map(function (x) return {
                                        selected: data.selectedIds.indexOf(x.id) >= 0,
                                        item: x
                                    });
                    var hasPrev = offset > 0;
                    var hasNext = users.length > MemberCandidateSize;
                    binding.setProperty("offset", offset);
                    binding.setProperty("hasPrev", hasPrev);
                    binding.setProperty("hasNext", hasNext);
                    JsViews.observable(data.items).refresh(list);
                });
            }

            function filterSelectedMember() {
                return data.items
                            .filter(function (x) return x.selected)
                            .map(function (x) return x.item);
            }

            JsViews.observable(data.items).observeAll(function (e, args) {
                if (args.path == "selected") {
                    var user: User = e.target.item;
                    var ids = data.selectedIds.copy();
                    var b = JsViews.observable(data.selectedIds);
                    if (args.value) {
                        if (ids.indexOf(user.id) < 0) {
                            ids.push(user.id);
                            b.refresh(ids);
                        }
                    } else {
                        if (ids.remove(user.id)) {
                            b.refresh(ids);
                        }
                    }
                }
            });

            binding.setProperty("query", "");
            JsViews.observable(data.selectedIds).refresh([]);
            searchMemberCandidate();

            html.find("#member-search-form").on("submit", function (e: Event) {
                e.preventDefault();
                searchMemberCandidate(data.query);
            });

            html.find("#member-list-prev").on("click", function (_) {
                var query = data.query;
                var offset = data.offset - MemberCandidateSize;
                searchMemberCandidate(query, offset);
            });

            html.find("#member-list-next").on("click", function (_) {
                var query = data.query;
                var offset = data.offset + MemberCandidateSize;
                searchMemberCandidate(query, offset);
            });

            html.on("click", "#add-member-dialog-submit", function (e) {
                var role = data.isManager ? GroupRole.Manager : GroupRole.Member;
                var members = data.selectedIds
                                .map(function (x) return { userId: x, role:role });
                ctx.fulfill(members);
            });
        });
    }
}