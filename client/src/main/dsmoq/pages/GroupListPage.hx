package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.Async;
import dsmoq.models.Service;
import dsmoq.Page;
import hxgnd.js.Html;
import hxgnd.js.jsviews.JsViews;
import hxgnd.PositiveInt;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Unit;

class GroupListPage {
    public static function render(html: Html, onClose: Promise<Unit>, pageNum: PositiveInt): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var condition = {
            query: "",
            index: pageNum - 1
        };
        var binding = JsViews.observable({
            condition: condition,
            result: Async.Pending
        });

        View.getTemplate("group/list").link(html, binding.data());

        JsViews.observe(condition, "index", function (_, args) {
            var page = args.value + 1;
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(page)));
        });

        Service.instance.findGroups({ offset: 20 * (pageNum - 1) }).then(function (x) {
            binding.setProperty("result", Async.Completed({
                total: x.summary.total,
                items: x.results,
                pages: Math.ceil(x.summary.total / 20)
            }));

        }, function (err) {
            Notification.show("error", "error happened");
        });

        return navigation.promise;
    }
}