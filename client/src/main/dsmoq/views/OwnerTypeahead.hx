package dsmoq.views;

import haxe.Json;
import hxgnd.js.Html;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import js.html.KeyboardEvent;
import js.typeahead.Bloodhound;
import js.typeahead.Typeahead;
import js.html.Event;
import js.html.Element;
import hxgnd.Option;

using hxgnd.OptionTools;

class OwnerTypeahead {
    static var engine = createBloodhound();

    public static function initialize(target: Html): Void {
        Typeahead.initialize(target, {}, {
            source: engine.ttAdapter(),
            displayKey: "name",
            templates: {
                suggestion: function (x) {
                    return '<p>${x.name}</p>';
                },
                empty: null,
                footer: null,
                header: null
            }
        });

        var targetElem: TargetElement = cast target.get(0);
        targetElem.__initValue = target.val();
        //targetElem.__completedItem = resolve(target.val());

        target.on({
            "focus": function (_) {
                var val = target.val();
                targetElem.__initValue = val;
                targetElem.__completedItem = new PromiseBroker();
            },
            "typeahead:autocompleted": function (_) {
                Typeahead.close(target);
                if (targetElem.__initValue != target.val()) {
                    resolve(target.val()).then(targetElem.__completedItem.fulfill);
                    target.trigger("change");
                }
            },
            "typeahead:selected": function (_) {
                if (targetElem.__initValue != target.val()) {
                    resolve(target.val()).then(targetElem.__completedItem.fulfill);
                    target.trigger("change");
                }
            }
        });

        target.on("change", function (_) {
            trace("change");
            //if (!targetElem.__completed) {
                //var val = target.val();
                //engine.get(val, function (items: Array<Dynamic>) {
                    //if (items.length == 1 && items[0].name == val) {
                        //targetElem.__completed = true;
                        //trace("complate");
                        //// TODO completed valueをpromiseで設定
                    //}
                //});
            //}
        });
    }

    public static function getCompletedValue(target: Html): Promise<Option<Dynamic>> {
        var targetElem: TargetElement = cast target.get(0);
        return targetElem.__completedItem.promise;
    }

    static function resolve(query: String): Promise<Option<Dynamic>> {
        return new Promise(function (ctx) {
            if (query == "") {
                ctx.fulfill(Option.None);
            } else {
                engine.get(query, function (items: Array<Dynamic>) {
                    ctx.fulfill(if (items.length == 1 && items[0].name == query) {
                        Option.Some(items[0]);
                    } else {
                        Option.None;
                    });
                });
            }
        });
    }

    static function createBloodhound() {
        var engine = new Bloodhound({
            datumTokenizer: Bloodhound.tokenizers.obj.whitespace("name"),
            queryTokenizer: Bloodhound.tokenizers.whitespace,
            remote: {
                url: "/api/suggests/users_and_groups",
                replace: function (url, query) {
                    return '$url?query=$query';
                },
                filter: function (x: {status: String, data: Array<Dynamic>}) {
                    return (x.status == "OK") ? x.data : [];
                }
            }
        });
        engine.initialize();
        return engine;
    }
}

private typedef TargetElement = {>Element,
    __initValue: String,
    __completedItem: PromiseBroker<Option<Dynamic>>
}