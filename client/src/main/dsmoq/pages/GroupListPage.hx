package dsmoq.pages;

import dsmoq.Async;
import dsmoq.models.Service;
import dsmoq.Page;
import hxgnd.PositiveInt;
import hxgnd.StreamBroker;
import js.html.Element;
import hxgnd.Promise;
import hxgnd.Unit;
import hxgnd.Stream;
import conduitbox.PageNavigation;
import hxgnd.js.Html;
import hxgnd.js.jsviews.JsViews;

class GroupListPage {
    public static function render(html: Html, onClose: Promise<Unit>, pageNum: PositiveInt): Stream<PageNavigation<Page>> {
        var navigation = new StreamBroker();

        var rootBinding = JsViews.observable({data: Async.Pending});
        View.getTemplate("group/list").link(html, rootBinding.data());

        Service.instance.findGroups({ offset: 20 * (pageNum - 1) }).then(function (x) {
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