package dsmoq.pages;

import js.Boot;
import js.bootstrap.BootstrapButton;
import js.support.ControllableStream;
import js.html.Element;
import js.jqhx.JqHtml;
import dsmoq.models.Service;
import dsmoq.framework.View;
import js.jsviews.JsViews;
import js.typeahead.Typeahead;
import js.typeahead.Bloodhound;
import dsmoq.models.Profile;
import dsmoq.models.GroupMember;
import dsmoq.framework.types.PageNavigation;
import js.jqhx.JQuery;

class GroupEditPage {
    public static function create(id: String) {
        var engine = createAccountEngine();
        var navigation = new ControllableStream();

        return {
            navigation: navigation,
            invalidate: function (container: Element) {
                var root = new JqHtml(container);
                var rootBinding = JsViews.objectObservable({data: Async.Pending});
                View.getTemplate("group/edit").link(container, rootBinding.data());

                Service.instance.getGroup(id).then(function (res) {
                    var data = {
                        myself: Service.instance.profile,
                        group: res,
                        groupErrors: {description: ""},
                        isMembersLoading: true,
                        members: {
                            summary: { count: 0, offset: 0, total: 0 },
                            results: []
                        },
                    };
                    var binding = JsViews.objectObservable(data);
                    rootBinding.setProperty("data", Async.Completed(data));

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
                        if (new JqHtml(e.target).val() != "") {
                            root.find("#group-icon-submit").show();
                        } else {
                            root.find("#group-icon-submit").hide();
                        }
                    });
                    root.find("#group-icon-submit").on("click", function (_) {
                        BootstrapButton.setLoading(root.find("#group-icon-submit"));
                        Service.instance.changeGroupImage(id, JQuery.find("#group-icon-form")).then(function (res) {
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
                        BootstrapButton.setLoading(root.find("#group-member-submit"));
                        root.find("#group-members").find("input,select,.btn").attr("disabled", true);
                        Service.instance.updateGroupMemberRoles(id, data.members.results).then(function (_) {
                            Notification.show("success", "save successful");
                        }, function (err) {
                            Notification.show("error", "error happened");
                        }, function () {
                            BootstrapButton.reset(root.find("#group-member-submit"));
                            root.find("#group-members").find("input,select,.btn").removeAttr("disabled");
                        });
                    });

                    root.find("#group-finish-editing").on("click", function (_) {
                        navigation.update(PageNavigation.Navigate(Page.GroupShow(id)));
                    });
                });
            },
            dispose: function () {
            }
        }
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