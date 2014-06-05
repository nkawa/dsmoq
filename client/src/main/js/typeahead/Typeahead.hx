package js.typeahead;

import js.jqhx.JqHtml;

class Typeahead {
    public static function initialize(
            html: JqHtml, ?options: TypeaheadOptions, ?dataset: TypeaheadDataset): JqHtml {
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

typedef TypeaheadDatasetOptions = {
    source : String -> (Array<Dynamic> -> Void) -> Void,
    ?name: String,
    ?displayKey: String,
    ?templates: {
        ?empty: Dynamic -> String,
        ?footer: Dynamic -> String,
        ?header: Dynamic -> String,
        ?suggestion: Dynamic -> String
    }
}

abstract TypeaheadDataset(Array<TypeaheadDatasetOptions>)
        from Array<TypeaheadDatasetOptions> to Array<TypeaheadDatasetOptions> {
    @:from static public inline function from(a: TypeaheadDatasetOptions) {
        return [a];
    }
}