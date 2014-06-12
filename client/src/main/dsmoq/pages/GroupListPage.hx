package dsmoq.pages;

import dsmoq.Async;
import js.support.PositiveInt;
import js.support.ControllableStream;
import js.html.Element;
import dsmoq.models.Service;
import js.jsviews.JsViews;
import dsmoq.framework.View;
import dsmoq.framework.types.PageNavigation;
import dsmoq.Page;

class GroupListPage {
    public static function create(page: PositiveInt) {
        var navigation = new ControllableStream();

        return {
            navigation: navigation,
            invalidate: function (container: Element) {
                var rootBinding = JsViews.objectObservable({data: Async.Pending});
                View.getTemplate("group/list").link(container, rootBinding.data());

                Service.instance.findGroups({ offset: 20 * (page - 1) }).then(function (x) {
                    var data = {
                        condition: { },
                        result: {
                            index: Math.ceil(x.summary.offset / 20),
                            total: x.summary.total,
                            items: x.results,
                            pages: Math.ceil(x.summary.total / 20)
                        }
                    };
                    rootBinding.setProperty("data", data);

                    JsViews.observe(data, "result.index", function (_, _) {
                        var page = data.result.index + 1;
                        navigation.update(PageNavigation.Navigate(Page.DatasetList(page)));
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