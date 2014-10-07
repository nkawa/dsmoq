package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.Async;
import dsmoq.models.Service;
import hxgnd.Error;
import hxgnd.js.Html;
import hxgnd.js.JqHtml;
import hxgnd.js.JQuery;
import hxgnd.js.jsviews.JsViews;
import hxgnd.PositiveInt;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Result;
import hxgnd.Unit;
import dsmoq.views.OwnerTypeahead;
import dsmoq.views.AttributeNameTypeahead;
import js.typeahead.Typeahead;
import dsmoq.views.AutoComplete;
import js.bootstrap.BootstrapPopover;

class DatasetListPage {
    public static function render(root: Html, onClose: Promise<Unit>, pageNum: PositiveInt): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var rootBinding = JsViews.observable({ data: Async.Pending });
        View.getTemplate("dataset/list").link(root, rootBinding.data());

        Service.instance.findDatasets({ offset: 20 * (pageNum - 1) }).then(function (x) {
            var data = {
                condition: {
                    query: "",
                    filters: []
                },
                result: {
                    index: Math.ceil(x.summary.offset / 20),
                    total: x.summary.total,
                    items: x.results,
                    pages: Math.ceil(x.summary.total / 20)
                }
            };
            rootBinding.setProperty("data", data);

            BootstrapPopover.initialize("#add-filter-button", {
                content: JQuery._("#filter-add-form").children(),
                placement: "bottom",
                html: true
            });

            //OwnerTypeahead.initialize("#filter-owner-input");
            AutoComplete.initialize("#filter-owner-input", {
                url: function (query: String) {
                    return '/api/suggests/users_and_groups?query=${query}';
                },
                path: "name",
                filter: function (data: Dynamic) {
                    return if (data.status == "OK" && Std.is(data.data, Array)) {
                        Result.Success(data.data);
                    } else {
                        Result.Failure(new Error("Network Error"));
                    }
                },
                template: {
                    suggestion: function (x) {
                        return '<div>${x.name}</div>';
                    }
                }
            });
            JQuery._("#filter-owner-input").on("autocomplete:complated", function (_) {
                JQuery._("#filter-owner-apply").attr("disabled", false);
            });
            JQuery._("#filter-owner-input").on("autocomplete:uncomplated", function (_) {
                JQuery._("#filter-owner-apply").attr("disabled", true);
            });

            JQuery._("#filter-owner-apply").on("click", function (_) {
                var item = AutoComplete.getCompletedItem("#filter-owner-input");
                JsViews.observable(data.condition.filters).insert({
                    type: 'owner',
                    item: item
                });
                AutoComplete.clear("#filter-owner-input");
                BootstrapPopover.hide("#add-filter-button");

                // TODO 再検索
            });


            AttributeNameTypeahead.initialize("#filter-attribute-name-input");



            JsViews.observe(data, "result.index", function (_, _) {
                var page = data.result.index + 1;
                navigation.fulfill(Navigation.Navigate(Page.DatasetList(page)));
            });
        }, function (err) {
            trace(err);
            Notification.show("error", "error happened");
        });

        return navigation.promise;
    }
}