package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.models.GroupMember;
import dsmoq.models.GroupRole;
import dsmoq.models.Profile;
import dsmoq.models.Service;
import dsmoq.models.User;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Stream;
import hxgnd.Unit;
import js.bootstrap.BootstrapButton;
import js.html.Event;

class GroupEditPage {
    inline static var MemberCandidateSize = 5;

    public static function render(root: Html, onClose: Promise<Unit>, id: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var rootData = {
            data: Async.Pending,
            memberCandidate: {
                query: "",
                offset: 0,
                hasPrev: false,
                hasNext: false,
                items: new Array<{selected: Bool, item: User}>(),
                selectedIds: new Array<String>()
            }
        };
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

            Service.instance.getGroupMembers(id).then(function (x) {
                binding.setProperty("members", Async.Completed({
                    index: Math.ceil(x.summary.offset / 20),
                    total: x.summary.total,
                    items: x.results,
                    pages: Math.ceil(x.summary.total / 20)
                }));
            });

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
            function searchMemberCandidate(?query: String, offset = 0) {
                var limit = MemberCandidateSize + 1;
                Service.instance.findUsers({ query: query, offset: offset, limit: limit }).then(function (users) {
                    var list = users.slice(0, MemberCandidateSize)
                                    .map(function (x) return {
                                        selected: rootData.memberCandidate.selectedIds.indexOf(x.id) >= 0,
                                        item: x
                                    });
                    var hasPrev = offset > 0;
                    var hasNext = users.length > MemberCandidateSize;
                    rootBinding.setProperty("memberCandidate.offset", offset);
                    rootBinding.setProperty("memberCandidate.hasPrev", hasPrev);
                    rootBinding.setProperty("memberCandidate.hasNext", hasNext);
                    JsViews.observable(rootData.memberCandidate.items).refresh(list);
                });
            }

            function filterSelectedMember() {
                return rootData.memberCandidate.items
                            .filter(function (x) return x.selected)
                            .map(function (x) return x.item);
            }


            root.find("#add-member-dialog").on('show.bs.modal', function (_) {
                // TODO なぜか初回しかコールされていない
                trace("show");
                rootBinding.setProperty("memberCandidate.query", "");
                JsViews.observable(rootData.memberCandidate.selectedIds).refresh([]);
                searchMemberCandidate();
            });

            root.find("#member-search-form").on("submit", function (e: Event) {
                e.preventDefault();
                searchMemberCandidate(rootData.memberCandidate.query);
            });

            JsViews.observable(rootData.memberCandidate.items).observeAll(function (e, args) {
                if (args.path == "selected") {
                    var user: User = e.target.item;
                    var ids = rootData.memberCandidate.selectedIds.copy();
                    var b = JsViews.observable(rootData.memberCandidate.selectedIds);
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

            root.find("#member-list-prev").on("click", function (_) {
                var query = rootData.memberCandidate.query;
                var offset = rootData.memberCandidate.offset - MemberCandidateSize;
                searchMemberCandidate(query, offset);
            });

            root.find("#member-list-next").on("click", function (_) {
                var query = rootData.memberCandidate.query;
                var offset = rootData.memberCandidate.offset + MemberCandidateSize;
                searchMemberCandidate(query, offset);
            });

            root.find("#add-member-dialog-submit").on("click", function (e) {
                // TODO mask
                BootstrapButton.setLoading(e.currentTarget);

                var members = rootData.memberCandidate.selectedIds
                                .map(function (x) return { userId: x, role: dsmoq.models.GroupRole.Member });

                Service.instance.addGroupMember(id, members);

                trace("submit");
            });

            root.find("#group-members").on("click", "[data-remove]", function (e) {
                var node = JQuery._(e.currentTarget);
                var userId = node.data("remove");
                Service.instance.removeGroupMember(id, userId);
            });


            //root.on("click", "#group-user-add", function (_) {
                //switch (data.members) {
                    //case Async.Completed(members):
                        //var name = Typeahead.getVal(root.find("#group-user-typeahead"));
                        //engine.get(name, function (res) {
                            //if (res.length == 1) {
                                //var item: GroupMember = {
                                    //id: res[0].id,
                                    //name: res[0].name,
                                    //fullname: res[0].fullname,
                                    //organization: res[0].organization,
                                    //title: res[0].title,
                                    //image: res[0].image,
                                    //role: dsmoq.models.GroupRole.Member
                                //};
                                //JsViews.observable(members.items).insert(item);
                            //}
                            //Typeahead.setVal(root.find("#group-user-typeahead"), "");
                        //});
                    //default:
                //}
            //});
            //root.on("change.dsmoq.pagination", "#member-pagination", function (_) {
                //switch (data.members) {
                    //case Async.Completed(list):
                        //Service.instance.getGroupMembers(id, {offset: 20 * untyped list.index}).then(function (x) {
                            //binding.setProperty("members", Async.Completed({
                                //index: Math.ceil(x.summary.offset / 20),
                                //total: x.summary.total,
                                //items: x.results,
                                //pages: Math.ceil(x.summary.total / 20) + 10
                            //}));
                            //setMemberTypeahead();
                        //});
                    //default:
                //}
            //});
            //root.on("click", "#group-member-submit", function (_) {
                //switch (data.members) {
                    //case Async.Completed(list):
                        //BootstrapButton.setLoading(root.find("#group-member-submit"));
                        //root.find("#group-members").find("input,select,.btn").attr("disabled", true);
                        //Service.instance.updateGroupMemberRoles(id, cast list.items).then(function (_) {
                            //Notification.show("success", "save successful");
                        //}, function (err) {
                            //Notification.show("error", "error happened");
                        //}, function () {
                            //BootstrapButton.reset(root.find("#group-member-submit"));
                            //root.find("#group-members").find("input,select,.btn").removeAttr("disabled");
                        //});
                    //default:
                //}
            //});

            // ----------------------------------
            root.find("#group-finish-editing").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(Page.GroupShow(id)));
            });
        });

        return navigation.promise;
    }
}