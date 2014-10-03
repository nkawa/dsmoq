package dsmoq.views;

import hxgnd.js.Html;
import js.typeahead.Typeahead;
import js.typeahead.Bloodhound;
import js.html.Event;

class AttributeNameTypeahead {
    static var engine = createBloodhound();

    public static function initialize(target: Html) : Void {
        Typeahead.initialize(target, {
            source: engine.ttAdapter(),
        });

        function trigger(e: Event) { target.trigger("change"); }
        target.find(".attribute-typeahead")
            .on("typeahead:autocompleted", trigger)
            .on("typeahead:selected", trigger);
    }

    static function createBloodhound() {
        var engine = new Bloodhound({
            datumTokenizer: Bloodhound.tokenizers.obj.whitespace("value"),
            queryTokenizer: Bloodhound.tokenizers.whitespace,
            remote: {
                url: "/api/suggests/attributes",
                replace: function (url, query) {
                    return '$url?query=$query';
                },
                filter: function (x: {status: String, data: Array<String>}) {
                    return (x.status == "OK") ? x.data.map(function (x) return {value: x}) : [];
                }
            }
        });
        engine.initialize();
        return engine;
    }
}