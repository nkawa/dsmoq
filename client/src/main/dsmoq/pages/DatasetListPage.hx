package dsmoq.pages;

import haxe.Json;
import conduitbox.Navigation;
import dsmoq.Async;
import dsmoq.models.Service;
import dsmoq.models.TagDetail;
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
import js.Lib;

class DatasetListPage {

    /**
     * 1ページあたりに表示するデータセットの件数
     */
    static inline var DATASET_LIMIT_PER_PAGE: Int = 20;

    public static function render(root: Html, onClose: Promise<Unit>, pageNum: Int, query: String, filters: Dynamic): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();
        var condition = {
            query: query,
            filters: filters,//new Array<{type: String, item: Dynamic}>(),
            index: pageNum - 1
        }
        var binding = JsViews.observable({
            condition: condition,
            result: Async.Pending,
            tag: new Array<TagDetail>()
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

            Service.instance.getTags().then(function(x) {
                binding.setProperty("tag", x);
                Service.instance.findDatasets({
                    query: (condition.query != "") ? condition.query : null,
                    owners: owners,
                    groups: groups,
                    attributes: attrs,
                    offset: DATASET_LIMIT_PER_PAGE * condition.index,
                    limit: DATASET_LIMIT_PER_PAGE
                }).then(function (x) {
                    binding.setProperty("result", Async.Completed({
                        total: x.summary.total,
                        items: x.results,
                        pages: Math.ceil(x.summary.total / DATASET_LIMIT_PER_PAGE)
                    }));
                });
            });
        }

        // observe binding
        JsViews.observe(condition, "filters", function (_, _) {
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(1, StringTools.urlEncode(condition.query), condition.filters)));
        });
        JsViews.observe(condition, "index", function (_, args) {
            var page = args.value + 1;
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(page, StringTools.urlEncode(condition.query), condition.filters)));
        });

        // init search form
        JQuery._("#search-button").on("click", function (_) {
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(1, StringTools.urlEncode(condition.query), condition.filters)));
        });

        JQuery._("#search-form").on("submit", function (_) {
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(1, StringTools.urlEncode(condition.query), condition.filters)));
            return false;
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

        JQuery._("#add-filter-button").on("shown.bs.popover", function (_) {
            // init owner tab
            JQuery._("#filter-owner-input").val("");
            AutoComplete.initialize("#filter-owner-input", {
                url: function (query: String) {
                    var d = Json.stringify({query: StringTools.urlEncode(query)});
                    return '/api/suggests/users_and_groups?d=${d}';
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
            JQuery._("#filter-owner-apply").on("click", function (_) {
                var item = AutoComplete.getCompletedItem("#filter-owner-input");
                var name = JQuery._("#filter-owner-input").val();
                var filter = if(item == null) { name: name, fullname: name, dataType: 1 } else item;
                JsViews.observable(binding.data().condition.filters).insert({
                    type: 'owner',
                    item: filter
                });
                AutoComplete.clear("#filter-owner-input");
                BootstrapPopover.hide("#add-filter-button");
            });

            // init attribute tab
            JQuery._("#filter-attribute-name-input").val("");
            JQuery._("#filter-attribute-value-input").val("");
            AutoComplete.initialize("#filter-attribute-name-input", {
                url: function (query: String) {
                    var d = Json.stringify({query: StringTools.urlEncode(query)});
                    return '/api/suggests/attributes?d=${d}';
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
            JQuery._("#filter-attribute-apply").on("click", function (_) {
                var item = AutoComplete.getCompletedItem("#filter-attribute-name-input");
                var name = if(item == null) JQuery._("#filter-attribute-name-input").val() else item;
                JsViews.observable(binding.data().condition.filters).insert({
                    type: "attribute",
                    item: { name: StringTools.urlEncode(name), value: StringTools.urlEncode(JQuery._("#filter-attribute-value-input").val()) }
                });
                AutoComplete.clear("#filter-attribute-name-input");
                JQuery._("#filter-attribute-value-input").val("");
                BootstrapPopover.hide("#add-filter-button");
            });
        });

        JQuery._("#conditions").on("click", ".list-group-item .close", function (e) {
            var i = JQuery._("#conditions .list-group-item").index(JQuery._(e.target).parents(".list-group-item"));
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
