package dsmoq.pages;

import haxe.Json;
import conduitbox.Navigation;
import dsmoq.Async;
import dsmoq.models.DatasetQuery;
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

typedef DatasetListConditionInput = {
    var type: String;
    var basic: String;
    var advanced: Array<Dynamic>;
};

class DatasetListPage {

    /**
     * 1ページあたりに表示するデータセットの件数
     */
    static inline var DATASET_LIMIT_PER_PAGE: Int = 20;

    static var DEFALUT_CONDITION_INPUT: DatasetListConditionInput = {
        type: "basic",
        basic: "",
        advanced: [{ id: 1, target: "query", operator: "contain", value: "" }]
    };

    public static function render(root: Html, onClose: Promise<Unit>, pageNum: Int, query: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();
        var data = {
            condition: conditionInputFromString(query),
            result: Async.Pending,
            index: pageNum - 1,
            tag: new Array<TagDetail>(),
            customQueries: new Array<{ data: DatasetQuery, url: String }>(),
            saveQueryName: "",
        };
        var condition = conditionFromConditionInput(data.condition);
        var binding = JsViews.observable(data);
        View.getTemplate("dataset/list").link(root, binding.data());

        JsViews.observe(data.condition, "index", function (_, args) {
            var page = args.value + 1;
            var q = StringTools.urlEncode(Json.stringify(condition));
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(page, q)));
        });

        root.find(".basic-search-tab").on("show.bs.tab", function (_) {
            binding.setProperty("condition.type", "basic");
            if (data.condition.advanced[0].target == "query") {
                binding.setProperty("condition.basic", data.condition.advanced[0].value);
            }
            root.find(".basic-query").focus();
        });
        root.find(".advanced-search-tab").on("show.bs.tab", function (_) {
            binding.setProperty("condition.type", "advanced");
            if (data.condition.advanced[0].target == "query") {
                JsViews.observable(data.condition.advanced[0]).setProperty("value", data.condition.basic);
            }
        });
        if (data.condition.type == "basic") {
            root.find(".basic-query").focus();
        }

        root.find(".search-button").on("click", function(_) {
            var c = conditionFromConditionInput(data.condition);
            var q = StringTools.urlEncode(Json.stringify(c));
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(1, q)));
        });

        var queryRows = root.find(".query-rows");
        queryRows.on("click", "button.add-row", function(e) {
            var el = Html.fromEventTarget(e.target);
            var row = el.closest(".query-row");
            var id = row.data("id");
            var index = 0;
            var maxId = 0;
            for (i in 0...data.condition.advanced.length) {
                var x = data.condition.advanced[i];
                if (x.id == id) {
                    index = i;
                }
                if (maxId < x.id) {
                    maxId = x.id;
                }
            }
            var item = { id: maxId + 1, target: "query", operator: "contain", value: "" };
            JsViews.observable(data.condition.advanced).insert(index + 1, item);
        });
        queryRows.on("click", "button.delete-row", function(e) {
            var el = Html.fromEventTarget(e.target);
            var row = el.closest(".query-row");
            var id = row.data("id");
            var xs: Dynamic = data.condition.advanced;
            var index = xs.findIndex(function(x) { return x.id == id; });
            JsViews.observable(data.condition.advanced).remove(index);
        });
        // TODO: suggests

        JsViews.observable(data.condition.advanced).observeAll(function(e, args) {
            if (args.path != "target") {
                return;
            }
            var obj: Dynamic = switch (e.target.target) {
                case "query": { operator: "contain", value: "" };
                case "owner": { operator: "equal", value: "" };
                case "tag": { value: if (data.tag.length == 0) "" else data.tag[0].tag };
                case "attribute": { key: "", value: "" };
                case "total-size": { operator: "ge", value: 0, unit: "byte" };
                case "public": { value: "public" };
                case "num-of-files": { operator: "ge", value: 0 };
                default: { value: "" };
            }
            JsViews.observable(e.target).setProperty(obj);
        });

        Service.instance.getTags().then(function(x) {
            binding.setProperty("tag", x);
        });

        function loadQueries() {
            return Service.instance.getDatasetQueries().then(function(xs) {
                var qs = xs.map(function(x) {
                    var q = StringTools.urlEncode(Json.stringify(x.query));
                    return { data: x, url: '/datasets?query=${q}' };
                });
                JsViews.observable(data.customQueries).refresh(qs);
            });
        }
        var saveQueryModal: Dynamic = root.find("#save-query-modal");
        root.find(".query-save-button").on("click", function(_) {
            saveQueryModal.modal("show");
            binding.setProperty("saveQueryName", "");
        });
        root.find(".save-query").on("click", function(_) {
            var c = conditionFromConditionInput(data.condition);
            Service.instance.addDatasetQuery(data.saveQueryName, c).then(
                function(_) {
                    loadQueries().then(function(_) {
                        Notification.show("success", "save successful");
                        saveQueryModal.modal("hide");
                    });
                }
            );
        });
        root.on("click", ".delete-query", function(e) {
            // TODO: show confirm dialog
            var el = Html.fromEventTarget(e.target);
            var row = el.closest(".saved-query");
            var id = row.data("id");
            Service.instance.deleteDatasetQuery(id).then(
                function(_) {
                    loadQueries().then(function(_) {
                        Notification.show("success", "save successful");
                    });
                }
            );
        });
        root.on("click", ".saved-query-link", function(e) {
            var el = Html.fromEventTarget(e.target);
            var row = el.closest(".saved-query");
            var id = row.data("id");
            var c = data.customQueries.filter(function(x) { return x.data.id == id; })[0].data.query;
            var q = StringTools.urlEncode(Json.stringify(c));
            navigation.fulfill(Navigation.Navigate(Page.DatasetList(1, q)));
            return false;
        });
        loadQueries();

        Service.instance.searchDatasets({
            query: condition,
            offset: DATASET_LIMIT_PER_PAGE * data.index,
            limit: DATASET_LIMIT_PER_PAGE
        }).then(function (x) {
            binding.setProperty("result", Async.Completed({
                total: x.summary.total,
                items: x.results,
                pages: Math.ceil(x.summary.total / DATASET_LIMIT_PER_PAGE)
            }));
        });

        return navigation.promise;
    }

    public static function conditionInputFromString(str: String): DatasetListConditionInput {
        if (str == null || str == "") {
            return DEFALUT_CONDITION_INPUT;
        }
        try {
            var json = Json.parse(str);
            return conditionInputFromCondition(json);
        } catch (e: Dynamic) {
            trace(e);
            return DEFALUT_CONDITION_INPUT;
        }
    }

    public static function conditionInputFromCondition(condition: Dynamic): DatasetListConditionInput {
        var cs = flattenCondition(condition);
        var advanced = if (cs.length == 0) {
            [{ target: "query", operator: "contain", value: "" }];
        } else {
            cs;
        };
        for (i in 0...advanced.length) {
            advanced[i].id = i + 1;
        }
        var head = advanced[0];
        var basic = if (head.target == "query") head.value else "";
        var type = if (advanced.length == 1 && head.target == "query" && head.operator == "contain") "basic" else "advanced";
        return { type: type, basic: basic, advanced: advanced };
    }

    public static function flattenCondition(condition: Dynamic): Array<Dynamic> {
        var ret = [];
        switch (condition.operator) {
            case "and":
                var vs: Array<Dynamic> = condition.value;
                for (v in vs) {
                    ret = ret.concat(flattenCondition(v));
                }
            case "or":
                var vs: Array<Dynamic> = condition.value;
                for (v in vs) {
                    ret = ret.concat(flattenCondition(v));
                    ret.push({ target: "or" });
                }
                ret.pop();
            default:
                ret.push(condition);
        }
        return ret;
    }

    public static function conditionFromConditionInput(input: DatasetListConditionInput): Dynamic {
        if (input.type == "basic") {
            return { target: "query", operator: "contain", value: input.basic };
        }
        var ors = [];
        var ands = [];
        for (ele in input.advanced) {
            if (ele.target == "or") {
                if (ands.length != 0) {
                    ors.push({ operator: "and", value: ands });
                    ands = [];
                }
            } else {
                var ce = conditionElementFromInputElement(ele);
                if (ce != null) {
                    ands.push(ce);
                }
            }
        }
        if (ands.length != 0) {
            ors.push({ operator: "and", value: ands });
        }
        return { operator: "or", value: ors };
    }

    public static function conditionElementFromInputElement(input: Dynamic): Dynamic {
        return switch (input.target) {
            case "query":        { target: input.target, value: input.value, operator: input.operator };
            case "owner":        { target: input.target, value: input.value, operator: input.operator };
            case "tag":          { target: input.target, value: input.value };
            case "attribute":    { target: input.target, value: input.value, key: input.key };
            case "total-size":   { target: input.target, value: toFloat(input.value, 0), operator: input.operator, unit: input.unit };
            case "public":       { target: input.target, value: input.value };
            case "num-of-files": { target: input.target, value: Std.int(toFloat(input.value, 0)), operator: input.operator };
            default: null;
        };
    }

    public static function toFloat(s: String, defaultValue: Null<Float> = null): Float {
        var x = Std.parseFloat(s);
        if (Math.isNaN(x) && defaultValue != null) {
            return defaultValue;
        }
        return x;
    }
}
