package framework;

import framework.helpers.Connection;
import framework.helpers.Core;
import pushstate.PushState;
import js.Browser.document;
import framework.Types;

typedef PageInfo = {
    path: String,
    attributes: Map<String, Dynamic>
}


class Effect{
    public inline static var DEFAULT_FIELD = "query";
    static var NoCallback = {};

    static var singleton: Effect = null;

    var handler: PageInfo -> Void;
    var firstAccess = true;

    var connections: Array<Void -> ConnectionStatus> = null;

    private function new(handler){
        PushState.init();
        PushState.addEventListener(onUrlChange);
        this.handler = handler;
        this.connections = [];
    }

    public static function global(){
        return singleton;
    }

    public static function initialize(handler: PageInfo -> Void){
        if(singleton != null){
            throw "Already initialized: GlobalEffect";
        }
        singleton = new Effect(handler);
    }

    public function changeUrl(pageInfo: PageInfo){
        var location = fromPageInfo(pageInfo);
        PushState.push(location);
    }

    public function updateHash(value: Dynamic){
        changeAttribute(DEFAULT_FIELD, value);
    }

    public function changeAttribute(key: String, value: Dynamic){
        var pageInfo= location();
        pageInfo.attributes.set(key, value);
        var location = fromPageInfo(pageInfo);
        PushState.push(location, NoCallback);
    }

    public function observeConnection(c: Void -> ConnectionStatus){
        connections.push(c);
    }

    public function location(){
        return toPageInfo(PushState.parseUrl(document.location.href));
    }

    private function onUrlChange(location: Location, state: Dynamic){
        if(state != NoCallback){
            handler(toPageInfo(location));
        }
    }
    private function fromPageInfo(pageInfo: PageInfo): Location{
        function stringifyHash(map: Map<String, Dynamic>){
            return haxe.Json.stringify(map.get(DEFAULT_FIELD));
        }
        return {
            path: pageInfo.path,
            hash: stringifyHash(pageInfo.attributes)
        };
    }
    private function toPageInfo(location: Location): PageInfo{
        function parseHash(hash: String){
            var map = new Map<String, Dynamic>();
            if(hash != null) try{
                map.set(DEFAULT_FIELD, haxe.Json.parse(hash));
            }catch(x:Dynamic){
                trace('Illegal format hash:$hash    reason:${Std.string(x)}');
            }
            return map;
        }
        return {
            path: location.path,
            attributes: parseHash(location.hash)
        };
    }

    public function notifyError(message: String, detail: Dynamic = null){
        var msg = switch(message){                   // failed by jQuery ajax-method
            case "timeout": Messages.timeout;
            case "error": Messages.connectionFailure;
            case "notmodified": Messages.notModified;
            case "parsererror": Messages.parseError;
            default: message;
        }
        var display = {
            level: "Error! ",
            message: msg,
            detail: detail,
            showDetail: detail != null && detail != ""
        };
        var html = framework.helpers.Templates.create("NotificationPanel").render(display).html;
        if(detail != null){
            html.find('.notification-detail').text(detail);
        }
        JQuery.j('#notification').empty().append(html);
    }

    public function connectionError(x: Dynamic){
        trace(Std.string(x));
        notifyError(Messages.connectionFailure, x.statusText);
    }
}

class Address{
    public static function url(s: String, v: Option<Dynamic> = null): PageInfo{ 
        var map = new Map<String, Dynamic>();
        Core.each(v, function(v){
            map.set(Effect.DEFAULT_FIELD, v);
        });
        return {path: s, attributes: map}; 
    }

    public static function hash(pageInfo: PageInfo){
        var v = pageInfo.attributes.get(Effect.DEFAULT_FIELD);
        return Core.option(v);
    }
}
