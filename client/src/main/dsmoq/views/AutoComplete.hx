package dsmoq.views;

import hxgnd.Error;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.JsTools;
import hxgnd.Option;
import hxgnd.Promise;
import hxgnd.Result;
import hxgnd.Stream;
import js.html.Element;
import js.html.Event;
import js.html.InputElement;
import js.html.KeyboardEvent;

using hxgnd.ArrayTools;
using hxgnd.OptionTools;
using hxgnd.ResultTools;

/**
 * AutoCompleteコンポーネント
 */
class AutoComplete {
    public static function initialize(target: Html, option: AutoCompleteOption): Void {
        var elem: TargetElement = cast target.get(0);
        var suggestion = JQuery._("<div class='autocomplete-suggestion'></div>");
        var isOpen = false;
        var selections = new Array<Dynamic>();
        var selectedIndex = Option.None;

        var template = if (option.template != null && option.template.suggestion != null) {
            option.template.suggestion;
        } else {
            function (x) return '<div>$x</div>';
        }

        var resolve: Dynamic -> Dynamic = if (option.path != null) {
            var tokens = option.path.split(".");
            function (obj) {
                for (k in tokens) {
                    obj = Reflect.field(obj, k);
                    if (obj == null) break;
                }
                return obj;
            }
        } else {
            function (x) return x;
        }

        function request(value: String): Promise<Suggestion> {
            return if (value == "") {
                Promise.fulfilled({ value: "", items: [] });
            } else {
                var url = option.url(value);
                var promise = JQuery.get(url).toPromise();
                promise.map(function (x) {
                    return if (option.filter == null) {
                        { value: value, items: x };
                    } else {
                        var items = option.filter(x).get();
                        { value: value, items: option.filter(x).get() };
                    }
                });
            }
        }

        function complete(i: Int) {
            if (selections[i] != null) {
                var item = selections[i];
                elem.__coumpleted = item;
                target.val(resolve(item));
                target.trigger("autocomplete:complated");
            }
        }

        function uncomplete() {
            if (elem.__coumpleted != null) {
                elem.__coumpleted = null;
                target.trigger("autocomplete:uncomplated");
            }
        }

        function close() {
            suggestion.empty();
            suggestion.remove();
            selectedIndex = Option.None;
            isOpen = false;
        }

        function open(newSelections: Array<Dynamic>) {
            var html = ArrayTools.mapWithIndex(newSelections, function (x, i) {
                return '<div class="autocomplete-suggestion-item" data-index="${i}">${template(x)}</div>';
            }).join("");

            var position = target.offset();
            var width = target.outerWidth();
            var height = target.outerHeight();

            suggestion.html(html);
            suggestion.css({
                left: '${position.left}px',
                top: '${position.top + height + 5}px',
                minWidth: '${width}px'
            });
            suggestion.on("mouseenter.autocomplete", ".autocomplete-suggestion-item", function (e: Event) {
                JQuery._(e.currentTarget).addClass("autocomplete-suggestion-item-hover");
            });
            suggestion.on("mouseleave.autocomplete", ".autocomplete-suggestion-item", function (e: Event) {
                JQuery._(e.currentTarget).removeClass("autocomplete-suggestion-item-hover");
            });
            suggestion.on("mousedown.autocomplete", ".autocomplete-suggestion-item", function (e: Event) {
                var index: Int = JQuery._(e.currentTarget).data("index");
                complete(index);
                close();
            });
            suggestion.appendTo("body");

            selections = newSelections;
            selectedIndex = Option.None;
            isOpen = true;
        }

        function select(i: Int) {
            var selected = suggestion.children('*:nth-of-type(${i + 1})');

            suggestion.children("*.autocomplete-suggestion-item-selected")
                .removeClass("autocomplete-suggestion-item-selected");
            selected.addClass("autocomplete-suggestion-item-selected");

            var position = selected.position();
            if (position.top < 0) {
                var padding = Std.parseInt(suggestion.css("padding-bottom"));
                suggestion.scrollTop(suggestion.scrollTop() + position.top - padding);
            } else {
                var height = suggestion.innerHeight();
                var bottom = position.top + selected.outerHeight();
                var offset = bottom - height;
                var padding = Std.parseInt(suggestion.css("padding-bottom"));
                if (offset > 0) {
                    suggestion.scrollTop(suggestion.scrollTop() + offset + padding);
                }
            }

            selectedIndex = Option.Some(i);
        }

        function selectNext() {
            var i = (selectedIndex.getOrDefault(-1) + 1) % selections.length;
            select(i);
        }

        function selectPrev() {
            var i = selectedIndex.getOrDefault(selections.length) - 1;
            i = (i + selections.length) % selections.length;
            select(i);
        }

        function setValue(value: String) {
            request(value).then(function (record) {
                target.val(value);
                if (record.items.length == 1 && resolve(record.items[0]) == value) {
                    elem.__coumpleted = record.items[0];
                } else {
                    elem.__coumpleted = null;
                }
            });
        }

        target.on("keydown.autocomplete", function (e: Event) {
            if (!isOpen) return;

            var event: KeyboardEvent = cast e;
            switch (event.keyCode) {
                case 40: //down
                    selectNext();
                    e.preventDefault();
                case 38: //up
                    selectPrev();
                    e.preventDefault();
                case 9: //tab
                    switch(selectedIndex) {
                        case Some(i):
                            if (selections.length == 1) {
                                complete(i);
                                close();
                            } else {
                                if (event.shiftKey) {
                                    selectPrev();
                                } else {
                                    selectNext();
                                }
                            }
                        case None:
                            select(0);
                    }
                    e.preventDefault();
                case 13: //enter
                    complete(selectedIndex.getOrDefault(-1));
                    close();
                    e.preventDefault();
                case 27: //esc
                    close();
                    e.preventDefault();
                default:
            }
        });

        target.on("blur.autocomplete", function (_) close());

        target.on("keydown.autocomplete", function (e: Event) {
            if (!isOpen) return;

            var event: KeyboardEvent = cast e;
            switch (event.keyCode) {
                case 40: //down
                    selectNext();
                    e.preventDefault();
                case 38: //up
                    selectPrev();
                    e.preventDefault();
                case 9: //tab
                    switch(selectedIndex) {
                        case Some(i):
                            if (selections.length == 1) {
                                complete(i);
                                close();
                            } else {
                                if (event.shiftKey) {
                                    selectPrev();
                                } else {
                                    selectNext();
                                }
                            }
                        case None:
                            select(0);
                    }
                    e.preventDefault();
                case 13: //enter
                    complete(selectedIndex.getOrDefault(-1));
                    close();
                    e.preventDefault();
                case 27: //esc
                    close();
                    e.preventDefault();
                default:
            }
        });

        var stream = target.asEventStream("input.autocomplete")
                        .debounce(400)
                        .map(function (e: Event) {
                            var elem: InputElement = cast e.currentTarget;
                            return elem.value;
                        });
        stream.then(function (_) {
            uncomplete();
        });
        stream.flatMapLastest(request).then(function (record) {
            if (record.items.length > 0) {
                if (record.items.length == 1 && record.value == resolve(record.items[0])) {
                    complete(0);
                    close();
                } else {
                    open(record.items);
                }
            } else {
                close();
            }
        }, function (e: Error) {
            trace(e);
        });

        elem.__coumpleted = null;
        elem.__setValue = setValue;
        elem.__stream = stream;
    }

    public static function setValue(target: Html, value: String): Void {
        getElement(target).iter(function (elem) {
            elem.__setValue(value);
        });
    }

    public static function getValue(target: Html): String {
        return switch (getElement(target)) {
            case Some(_): target.val();
            case None: "";
        }
    }

    public static function getCompletedItem(target: Html): Dynamic {
        return switch (getElement(target)) {
            case Some(elem): elem.__coumpleted;
            case None: "";
        }
    }

    public static function destroy(target: Html) : Void {
        getElement(target).iter(function (elem) {
            elem.__stream.cancel();
            JsTools.delete(elem, "__stream");
            JsTools.delete(elem, "__setValue");
            JsTools.delete(elem, "__coumpleted");
            target.off(".autocomplete");
        });
    }

    static function getElement(target: Html): Option<TargetElement> {
        return if (target.length > 0) {
            var elem: TargetElement = cast target.get(0);
            (elem.__stream != null) ? Option.Some(elem) : Option.None;
        } else {
            Option.None;
        }
    }
}

typedef AutoCompleteOption = {
    url: String -> String,
    ?path: String,
    ?filter: Dynamic -> Result<Array<Dynamic>>,
    ?template: {
        ?suggestion: Dynamic -> String,
    }
}

private typedef Suggestion = { value: String, items: Array<Dynamic> }

private typedef TargetElement = {>Element,
    __stream: Stream<String>,
    __setValue: String -> Void,
    __coumpleted: Dynamic
}