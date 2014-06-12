package dsmoq.pages;

import dsmoq.Async;
import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.View;
import dsmoq.models.Service;
import js.html.Element;
import js.jqhx.JqHtml;
import js.jsviews.JsViews;
import js.support.ControllableStream;
import js.support.PositiveInt;

class DatasetListPage {
    public static function create(page: PositiveInt): PageContent<Page> {
        var navigation = new ControllableStream();

        return {
            navigation: navigation,
            invalidate: function (container: Element) {
                var root = new JqHtml(container);

                var rootBinding = JsViews.objectObservable({ data: Async.Pending });
                View.getTemplate("dataset/list").link(container, rootBinding.data());

                Service.instance.findDatasets({ offset: 20 * (page - 1) }).then(function (x) {
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
        };
    }
}