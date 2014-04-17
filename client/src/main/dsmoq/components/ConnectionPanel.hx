package dsmoq.components;

import dsmoq.framework.Types;
import dsmoq.framework.helpers.Components;
import dsmoq.framework.helpers.Connection;
import dsmoq.framework.helpers.Promises;
import dsmoq.framework.JQuery;
import promhx.Promise;
import dsmoq.components.LoadingPanel;
import promhx.Stream.Stream;

class ConnectionPanel {
    public static function request<TEvent>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, TEvent>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpRequest, ConnectionStatus, TEvent> {
        return requestContinually(waiting, name, Components.outMap(component, Outer), onError);
    }

    public static function requestContinually<TEvent>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, NextChange<HttpRequest, TEvent>>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpRequest, ConnectionStatus, TEvent> {
        return LoadingPanel.create(waiting, name, component, function (request) {
            // TODO エラーがロストする
            var ret = Connection.send(request);
            var stream = new Stream();
            ret.event.then(function (x) { stream.resolve(x); } );
            return { event: stream, state: ret.state };
        }, onError);
    }

    public static function requestByJson<TEvent>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, TEvent>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpJsonRequest, ConnectionStatus, TEvent> {
        return requestByJsonContinually(waiting, name, Components.outMap(component, Outer), onError);
    }

    public static function requestByJsonContinually<TEvent>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, NextChange<HttpJsonRequest,TEvent>>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpJsonRequest, ConnectionStatus, TEvent> {
        return LoadingPanel.create(waiting, name, component, function (request) {
            // TODO エラーがロストする
            var ret = Connection.sendJson(request);
            var stream = new Stream();
            ret.event.then(function (x) stream.resolve(x));
            return { event: stream, state: ret.state};
        }, onError);
    }
}
