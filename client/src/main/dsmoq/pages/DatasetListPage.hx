package dsmoq.pages;

import conduitbox.PageNavigation;
import dsmoq.Async;
import dsmoq.models.Service;
import hxgnd.js.Html;
import hxgnd.js.JqHtml;
import hxgnd.js.jsviews.JsViews;
import hxgnd.PositiveInt;
import hxgnd.Promise;
import hxgnd.Stream;
import hxgnd.StreamBroker;
import hxgnd.Unit;

class DatasetListPage {
    public static function render(html: Html, onClose: Promise<Unit>, pageNum: PositiveInt): Stream<PageNavigation<Page>> {
        var navigation = new StreamBroker();

        var rootBinding = JsViews.observable({ data: Async.Pending });
        View.getTemplate("dataset/list").link(html, rootBinding.data());

        Service.instance.findDatasets({ offset: 20 * (pageNum - 1) }).then(function (x) {
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

        return navigation.stream;
    }
}