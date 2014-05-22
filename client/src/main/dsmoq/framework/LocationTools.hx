package dsmoq.framework;

import js.Browser;
import dsmoq.framework.types.Location;
using Lambda;

/**
 * ...
 * @author terurou
 */
class LocationTools {
    public static function currentLocation(): Location {
        return toLocation(Browser.location);
    }

    public static function currentUrl(): String {
        return toUrl(toLocation(Browser.location));
    }

    public static function toLocation(x: {pathname: String, search: String, hash: String}): Location {
        function toQueryMap(search: String) {
            var map = new Map();
            if (search.length > 0) {
                for (item in search.substring(1).split("&")) {
                    var tokens = item.split("=");
                    var key = StringTools.urlDecode(tokens[0]);
                    var val = (tokens[1] == null) ? "" : StringTools.urlDecode(tokens[1]);
                    map[key] = val;
                }
            }
            return map;
        }
        return {
            path: x.pathname,
            query: toQueryMap(x.search),
            hash: x.hash
        };
    }

    public static function toUrl<TPage: EnumValue>(location: Location): String {
        function toQuery(query: Null<Map<String, String>>) {
            var entries = [];
            if (query != null) {
                for (k in query.keys()) {
                    entries.push('${StringTools.htmlEscape(k)}=${StringTools.htmlEscape(query.get(k))}');
                }
            }
            return entries.empty() ? "" : '?${entries.join("&")}';
        }

        function toHash(hash: Null<String>) {
            return (hash == null || hash == "") ? "": '#$hash';
        }

        return '${location.path}${toQuery(location.query)}${toHash(location.hash)}';
    }
}
