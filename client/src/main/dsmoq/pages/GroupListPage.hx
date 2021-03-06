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
import hxgnd.js.JQuery;
import js.Lib;

class GroupListPage {
    public static function render(html: Html, onClose: Promise<Unit>, pageNum: PositiveInt, query: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var condition = {
            query: query,
            index: pageNum - 1
        };
        var binding = JsViews.observable({
            condition: condition,
            result: Async.Pending
        });

        View.getTemplate("group/list").link(html, binding.data());

        function load() {
            binding.setProperty("result", Async.Pending);
            Service.instance.findGroups({
                query: (condition.query != "") ? condition.query : null,
                offset: 20 * condition.index,
                limit: 20
            }).then(function (x) {
                binding.setProperty("result", Async.Completed({
                    total: x.summary.total,
                    items: x.results,
                    pages: Math.ceil(x.summary.total / 20)
                }));
            });
        }

        // observe binding
        JsViews.observe(condition, "index", function (_, args) {
            var page = args.value + 1;
            navigation.fulfill(Navigation.Navigate(Page.GroupList(page, StringTools.urlEncode(condition.query))));
        });

        // init search form
        function search() {
            var q = StringTools.urlEncode(condition.query);
            if (q == query && pageNum == 1) {
                // 同一URLになる場合、URL変更イベントが発火しないので、内部で再検索処理を行う
                load();
            } else {
                navigation.fulfill(Navigation.Navigate(Page.GroupList(1, q)));            
            }
        }
        JQuery._("#search-button").on("click", function (_) {
            search();
        });

        JQuery._("#search-form").on("submit", function (_) {
            search();
            return false;
        });
        
        load();

        return navigation.promise;
    }
}
