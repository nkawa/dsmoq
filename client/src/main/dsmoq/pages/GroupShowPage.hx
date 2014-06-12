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

class GroupShowPage {
    public static function create(id: String) {
        var navigation = new ControllableStream();
        return {
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
    }
}