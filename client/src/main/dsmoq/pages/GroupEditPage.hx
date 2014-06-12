package dsmoq.pages;

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
import js.jqhx.JQuery;

class GroupEditPage {
    public static function create(id: String) {
        return {
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
    }
}