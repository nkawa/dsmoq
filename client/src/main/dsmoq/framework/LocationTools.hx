package dsmoq.framework;

import js.Browser;
import dsmoq.framework.types.Location;
using Lambda;

/**
 * ...
 * @author terurou
 */
class LocationTools {
    public static function toLocation(x: {pathname: String, search: String, hash: String}): Location {
        function toQueryMap(search: String) {
            var map = new Map();
            return map;
        }

        return {
            path: Browser.location.pathname,
            query: toQueryMap(Browser.location.search),
            hash: Browser.location.hash
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
