package dsmoq.pages;

import dsmoq.Async;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.View;
import dsmoq.models.Service;
import dsmoq.Page;
import js.html.Element;
import js.jqhx.JqHtml;
import js.jsviews.JsViews;
import js.support.ControllableStream;
import js.support.JsTools;

using dsmoq.framework.helper.JQueryTools;

class GroupShowPage {
    public static function create(id: String) {
        var navigation = new ControllableStream();
        return {
            navigation: navigation,
            invalidate: function (container: Element) {
                var root = new JqHtml(container);
                var rootBinding = JsViews.objectObservable({ data: Async.Pending });
                View.getTemplate("group/show").link(root, rootBinding.data());

                Service.instance.getGroup(id).then(function (res) {
                    var data = {
                        myself: Service.instance.profile,
                        group: res,
                        members: Async.Pending,
                        datasets: Async.Pending,
                    };
                    var binding = JsViews.objectObservable(data);
                    rootBinding.setProperty("data", data);

                    Service.instance.getGroupMembers(id).then(function (x) {
                        var members = {
                            index: Math.ceil(x.summary.offset / 20),
                            total: x.summary.total,
                            items: x.results,
                            pages: Math.ceil(x.summary.total / 20)
                        };
                        binding.setProperty("members", Async.Completed(members));

                        JsViews.observe(members, "index", function (_, _) {
                            var i = members.index;
                            Service.instance.getGroupMembers(id, {offset: 20 * i}).then(function (x) {
                                var b = JsViews.objectObservable(members);
                                b.setProperty("index", i);
                                b.setProperty("total", x.summary.total);
                                b.setProperty("items", x.results);
                                b.setProperty("pages", Math.ceil(x.summary.total / 20));
                            }, function (e) {
                                Notification.show("error", "error happened");
                            });
                        });
                    }, function (err) {
                        Notification.show("error", "error happened");
                    });

                    Service.instance.findDatasets({group: id}).then(function (x) {
                        var datasets = {
                            index: Math.ceil(x.summary.offset / 20),
                            total: x.summary.total,
                            items: x.results,
                            pages: Math.ceil(x.summary.total / 20)
                        };
                        binding.setProperty("datasets", Async.Completed(datasets));

                        JsViews.observe(datasets, "index", function (_, _) {
                            var i = datasets.index;
                            Service.instance.findDatasets({group: id, offset: 20 * i}).then(function (x) {
                                var b = JsViews.objectObservable(datasets);
                                b.setProperty("index", i);
                                b.setProperty("total", x.summary.total);
                                b.setProperty("items", x.results);
                                b.setProperty("pages", Math.ceil(x.summary.total / 20));
                            }, function (e) {
                                Notification.show("error", "error happened");
                            });
                        });
                    }, function (err) {
                        Notification.show("error", "error happened");
                    });

                    root.find("#group-edit").on("click", function (_) {
                        navigation.update(PageNavigation.Navigate(GroupEdit(id)));
                    });

                    root.find("#group-delete").createEventStream("click").chain(function (_) {
                        return JsTools.confirm("Are you sure you want to delete this group?", false);
                    }).then(function (_) {
                        Service.instance.deleteGroup(id).then(function (_) {
                            Notification.show("success", "delete successful");
                            navigation.update(PageNavigation.Navigate(Page.GroupList(1)));
                        }, function (err) {
                            Notification.show("error", "error happened");
                        });
                    });
                }, function (err) {
                    trace(err);
                    root.html(switch (err.name) {
                        case ServiceErrorType.Unauthorized:
                            "Permission denied";
                        case ServiceErrorType.NotFound:
                            "Not found";
                        case _:
                            "Network Error";
                    });
                });
            },
            dispose: function () {
            }
        }
    }
}