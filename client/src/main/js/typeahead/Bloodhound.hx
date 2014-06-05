package js.typeahead;
import js.jqhx.JQuery;
import js.jqhx.JqXHR;

@:native("Bloodhound")
extern class Bloodhound<T> {
    function new(options: BloodhoundOptions<T>);
    function initialize(?reinitialize: Bool): Void;
    function add(datums: Array<T>): Void;
    function clear(): Void;
    function clearPrefetchCache(): Void;
    function get(query: String, cb: Array<T> -> Void): Void;
    function ttAdapter(): String -> (Array<T> -> Void) -> Void;

    static var tokenizers: {
        var nonword(default, null): String -> Array<String>;
        var whitespace(default, null): String -> Array<String>;
        var obj(default, null): {
            function nonword<T>(key: String): T -> Array<String>;
            function whitespace<T>(key: String): T -> Array<String>;
        };
    };
}

typedef BloodhoundOptions<T> = {
    datumTokenizer: T -> Array<String>,
    queryTokenizer: String -> Array<String>,
    ?limit: Int,
    ?dupDetector: T -> T -> Bool,
    ?sorter: T -> T -> Int,
    ?local: Array<T>,
    ?prefetch: BloodhoundPrefech<T>,
    ?remote: BloodhoundRemote<T>
}

abstract BloodhoundPrefech<T>(Dynamic) from BloodhoundPrefechOptions<T> from String {}
typedef BloodhoundPrefechOptions<T> = {
    url: String,
    ?cacheKey: String,
    ?ttl: Int,
    ?thumbprint: String,
    ?filter: Dynamic -> Array<T>,
    ?ajax: JqAjaxOption
}

abstract BloodhoundRemote<T>(Dynamic) from BloodhoundRemoteOptions<T> from String {}
typedef BloodhoundRemoteOptions<T> = {
    url: String,
    ?wildcard: String,
    ?replace: String -> String -> String,
    ?rateLimitBy: BloodhoundRateLimitBy,
    ?rateLimitWait: Int,
    ?filter: Dynamic -> Array<T>,
    ?ajax: JqAjaxOption
}

@:enum abstract BloodhoundRateLimitBy(String) {
    var Debounce = "debounce";
    var Throttle = "throttle";
}