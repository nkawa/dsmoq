package dsmoq.pages;

import js.support.ControllableStream;
import js.jqhx.JqHtml;
import dsmoq.models.Service;
import dsmoq.framework.View;
import js.jsviews.JsViews;
import dsmoq.framework.types.PageNavigation;
import dsmoq.Page;
import dsmoq.Async;
import js.html.Element;
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
                        binding.setProperty("members", Async.Completed(x));
                    }, function (err) {
                        Notification.show("error", "error happened");
                    });

                    Service.instance.findDatasets({group: id}).then(function (x) {
                        binding.setProperty("datasets", Async.Completed(x));
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
                    Notification.show("error", "error happened");
                });
            },
            dispose: function () {
            }
        }
    }
}