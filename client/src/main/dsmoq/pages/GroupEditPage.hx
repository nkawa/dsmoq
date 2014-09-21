package dsmoq.pages;

import conduitbox.PageNavigation;
import dsmoq.models.GroupMember;
import dsmoq.models.GroupRole;
import dsmoq.models.Profile;
import dsmoq.models.Service;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Stream;
import hxgnd.Unit;
import js.bootstrap.BootstrapButton;
import js.typeahead.Bloodhound;
import js.typeahead.Typeahead;

class GroupEditPage {
    public static function render(root: Html, onClose: Promise<Unit>, id: String): Promise<PageNavigation<Page>> {
        var engine = createAccountEngine();
        var navigation = new PromiseBroker();

        var rootBinding = JsViews.observable({data: Async.Pending});
        View.getTemplate("group/edit").link(root, rootBinding.data());

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

            function setMemberTypeahead() {
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
            }

            Service.instance.getGroupMembers(id).then(function (x) {
                binding.setProperty("members", Async.Completed({
                    index: Math.ceil(x.summary.offset / 20),
                    total: x.summary.total,
                    items: x.results,
                    pages: Math.ceil(x.summary.total / 20)
                }));
                setMemberTypeahead();
            });

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

            root.on("click", "#group-user-add", function (_) {
                switch (data.members) {
                    case Async.Completed(members):
                        var name = Typeahead.getVal(root.find("#group-user-typeahead"));
                        trace(name);
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
                                JsViews.observable(members.items).insert(item);
                            }
                            Typeahead.setVal(root.find("#group-user-typeahead"), "");
                        });
                    default:
                }
            });
            root.on("change.dsmoq.pagination", "#member-pagination", function (_) {
                switch (data.members) {
                    case Async.Completed(list):
                        Service.instance.getGroupMembers(id, {offset: 20 * untyped list.index}).then(function (x) {
                            binding.setProperty("members", Async.Completed({
                                index: Math.ceil(x.summary.offset / 20),
                                total: x.summary.total,
                                items: x.results,
                                pages: Math.ceil(x.summary.total / 20) + 10
                            }));
                            setMemberTypeahead();
                        });
                    default:
                }
            });
            root.on("click", "#group-member-submit", function (_) {
                switch (data.members) {
                    case Async.Completed(list):
                        BootstrapButton.setLoading(root.find("#group-member-submit"));
                        root.find("#group-members").find("input,select,.btn").attr("disabled", true);
                        Service.instance.updateGroupMemberRoles(id, cast list.items).then(function (_) {
                            Notification.show("success", "save successful");
                        }, function (err) {
                            Notification.show("error", "error happened");
                        }, function () {
                            BootstrapButton.reset(root.find("#group-member-submit"));
                            root.find("#group-members").find("input,select,.btn").removeAttr("disabled");
                        });
                    default:
                }
            });

            root.find("#group-finish-editing").on("click", function (_) {
                navigation.fulfill(PageNavigation.Navigate(Page.GroupShow(id)));
            });
        });

        return navigation.promise;
    }

    static function createAccountEngine() {
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
        return engine;
    }
}