package js.typeahead;

import hxgnd.js.JqHtml;

class Typeahead {
    public static function initialize<T>(
            html: JqHtml, ?options: TypeaheadOptions, ?dataset: TypeaheadDataset<T>): JqHtml {
        return untyped html.typeahead.apply(html, [cast options].concat(dataset));
    }

    public static function destroy(html: JqHtml): JqHtml {
        return untyped html.typeahead("destroy");
    }

    public static function open(html: JqHtml): JqHtml {
        return untyped html.typeahead("open");
    }

    public static function close(html: JqHtml): JqHtml {
        return untyped html.typeahead("close");
    }

    public static function getVal(html: JqHtml): String {
        return untyped html.typeahead("val");
    }

    public static function setVal(html: JqHtml, value: String): JqHtml {
        return untyped html.typeahead("val", value);
    }
}

typedef TypeaheadOptions = {
    ?highlight: Bool,
    ?hint: Bool,
    ?minLength: Int
}

typedef TypeaheadDatasetOptions<T> = {
    source : String -> (Array<T> -> Void) -> Void,
    ?name: String,
    ?displayKey: String,
    ?templates: {
        ?empty: { query: String } -> String,
        ?footer: { query: String, isEmpty: Bool } -> String,
        ?header: { query: String, isEmpty: Bool } -> String,
        ?suggestion: T -> String
    }
}

abstract TypeaheadDataset<T>(Array<TypeaheadDatasetOptions<T>>)
        from Array<TypeaheadDatasetOptions<T>> to Array<TypeaheadDatasetOptions<T>> {
    @:from static public inline function from(a: TypeaheadDatasetOptions<T>) {
        return [a];
    }
}