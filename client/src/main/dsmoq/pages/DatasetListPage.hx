package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.Async;
import dsmoq.models.Service;
import dsmoq.views.AutoComplete;
import hxgnd.Error;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.jsviews.JsViews;
import hxgnd.PositiveInt;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Result;
import hxgnd.Unit;
import js.bootstrap.BootstrapPopover;
import js.html.KeyboardEvent;

class DatasetListPage {
    public static function render(root: Html, onClose: Promise<Unit>, pageNum: PositiveInt): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var condition = {
            query: "",
            filters: new Array<{type: String, item: Dynamic}>(),
            index: pageNum - 1
        }
        var binding = JsViews.observable({
            condition: condition,
            result: Async.Pending
        });

        View.getTemplate("dataset/list").link(root, binding.data());

        function load() {
            var owners = condition.filters
                            .filter(function (x) return x.type == "owner" && x.item.dataType == 1)
                            .map(function (x) return x.item.name);
            var groups = condition.filters
                            .filter(function (x) return x.type == "owner" && x.item.dataType == 2)
                            .map(function (x) return x.item.name);
            var attrs = condition.filters
                            .filter(function (x) return x.type == "attribute")
                            .map(function (x) return x.item);

            binding.setProperty("result", Async.Pending);
            Service.instance.findDatasets({
                query: (condition.query != "") ? condition.query : null,
                owners: owners,
                groups: groups,
                attributes: attrs,
                offset: 20 * condition.index,
                limit: 20
            }).then(function (x) {
                binding.setProperty("result", Async.Completed({
                    total: x.summary.total,
                    items: x.results,
                    pages: Math.ceil(x.summary.total / 20)
                }));
            }, function (err) {
                trace(err);
                Notification.show("error", "error happened");
            });
        }

        // observe binding
        JsViews.observe(condition, "filters", function (_, _) {
            load();
        });
        JsViews.observe(condition, "index", function (_, args) {
            var page = args.value + 1;
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(page)));
        });

        // init search form
        JQuery._("#search-button").on("click", function (_) {
            load();
        });

        BootstrapPopover.initialize("#add-filter-button", {
            content: JQuery._("#filter-add-form").children(),
            placement: "bottom",
            html: true
        });
        JQuery._("body").on("keydown", function (e) {
            var event: KeyboardEvent = cast e;
            if (event.keyCode == 27) { //esc
                BootstrapPopover.hide("#add-filter-button");
            }
        });

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
            JsViews.observable(binding.data().condition.filters).insert({
                type: 'owner',
                item: item
            });
            AutoComplete.clear("#filter-owner-input");
            BootstrapPopover.hide("#add-filter-button");
        });

        AutoComplete.initialize("#filter-attribute-name-input", {
            url: function (query: String) {
                return '/api/suggests/attributes?query=${query}';
            },
            filter: function (data: Dynamic) {
                return if (data.status == "OK" && Std.is(data.data, Array)) {
                    Result.Success(data.data);
                } else {
                    Result.Failure(new Error("Network Error"));
                }
            },
            template: {
                suggestion: function (x) {
                    return '<div>${x}</div>';
                }
            }
        });
        JQuery._("#filter-attribute-name-input").on("autocomplete:complated", function (_) {
            JQuery._("#filter-attribute-apply").attr("disabled", false);
        });
        JQuery._("#filter-attribute-name-input").on("autocomplete:uncomplated", function (_) {
            JQuery._("#filter-attribute-apply").attr("disabled", true);
        });
        JQuery._("#filter-attribute-apply").on("click", function (_) {
            var name = AutoComplete.getCompletedItem("#filter-attribute-name-input");
            JsViews.observable(binding.data().condition.filters).insert({
                type: "attribute",
                item: { name: name, value: JQuery._("#filter-attribute-value-input").val() }
            });
            AutoComplete.clear("#filter-attribute-name-input");
            JQuery._("#filter-attribute-value-input").val("");
            BootstrapPopover.hide("#add-filter-button");
        });

        JQuery._("#conditions").on("click", "[data-index] .close", function (e) {
            var i = JQuery._(e.target).parents("[data-index]").data("index");
            JsViews.observable(condition.filters).remove(i);
        });

        // onClose
        onClose.then(function (_) {
            BootstrapPopover.destroy("#add-filter-button");
            AutoComplete.destroy("#filter-owner-input");
            AutoComplete.destroy("#filter-attribute-name-input");
        });

        // load init-data
        load();

        return navigation.promise;
    }
}