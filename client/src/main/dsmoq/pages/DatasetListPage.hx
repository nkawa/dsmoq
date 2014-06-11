package dsmoq.pages;

import dsmoq.framework.types.PageContent;
import dsmoq.framework.types.PageNavigation;
import js.support.ControllableStream;
import js.html.Element;
import js.support.PositiveInt;
import js.jqhx.JqHtml;
import js.jsviews.JsViews;
import dsmoq.models.Service;
import dsmoq.framework.View;

class DatasetListPage {
    public static function create(page: PositiveInt): PageContent<Page> {
        var navigation = new ControllableStream();

        return {
            navigation: navigation,
            invalidate: function (container: Element) {
                var root = new JqHtml(container);

                var data = {
                    condition: { },
                    result: { index: 0, total: 0, items: [], pages: 0 }
                };
                var binding = JsViews.objectObservable(data);

                Service.instance.findDatasets({ offset: 20 * (page - 1) }).then(function (x) {
                    binding.setProperty("result.index", Math.ceil(x.summary.offset / 20));
                    binding.setProperty("result.pages", Math.ceil(x.summary.total / 20));
                    binding.setProperty("result.total", x.summary.total);
                    binding.setProperty("result.items", x.results);
                    View.getTemplate("dataset/list").link(container, binding.data());

                    JsViews.observe(data, "result.index", function (_, _) {
                        var page = data.result.index + 1;
                        navigation.update(PageNavigation.Navigate(Page.DatasetList(page)));
                    });
                }, function (err) {
                    // TODO
                });
            },
            dispose: function () {
            }
        };
    }

}