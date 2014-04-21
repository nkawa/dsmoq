package dsmoq.framework.helpers;

import promhx.Promise;
import dsmoq.framework.types.Json;
import haxe.Json in JsonLib;
import dsmoq.framework.types.Html;

enum HttpMethod{ Get; Post; Put; Delete;}

typedef HttpRequest = {method: HttpMethod, url: String, params: Dynamic}
typedef HttpJsonRequest = {url: String, json: Json}
typedef HttpProcess<A> = {event: Promise<A>, state: Void -> ConnectionStatus}

private extern class CancelToken{     // jqXHR actually
    function abort():Void;
    var readyState:Int;  // 0 - unsent
}

enum ConnectionStatus{
    BeforeConnected(cancel: Void -> Void);
    DuringConnection(cancel: Void-> Void);
    Done;
    Failed;
}


class Connection {
    private static var jq = untyped __js__('$');

    public static function then<A, B>(p: HttpProcess<A>, f: A -> B): HttpProcess<B>{
        return {event: p.event.then(f), state: p.state};
    }

    public static function send<A>(request: HttpRequest): HttpProcess<A>{
        var p = new Promise();
        var token: CancelToken = jq.ajax({
            url: request.url,
            type: Std.string(request.method).toUpperCase(),
            data: request.params,
            dataType: 'json',
            success: p.resolve,
            error: p.reject
        });
        function state(){
            return if(p.isResolved()){
                Done;
            }else if(p.isRejected()){
                Failed;
            }else if(token.readyState == 0){
                BeforeConnected(token.abort);
            }else{
                DuringConnection(token.abort);
            };
        }
        return {event: p, state: state};
    }

    public static function sendJson<A>(request: HttpJsonRequest): HttpProcess<A>{
        return send({url: request.url, method: Post, params: JsonLib.stringify(request.json)});

    }
    public static function abort<A>(process: HttpProcess<A>){
        switch(process.state()){
            case BeforeConnected(cancel) | DuringConnection(cancel):  cancel();
            case Done | Failed:
        }
    }

    public static function ajaxSubmit<A>(form: Html, url: String): Promise<A>{
        return Promises.tap(function(p){
            var jqXHR = (untyped form).ajaxSubmit({ url: url, dataType:"JSON"}).data('jqxhr');
            // TODO: Resource Management
            jqXHR.then(function(response){
                p.resolve(response);
            }, function(_, text, ex){
                dsmoq.framework.Effect.global().notifyError(text, ex);
                p.reject(ex);
            });
        });
    }
}