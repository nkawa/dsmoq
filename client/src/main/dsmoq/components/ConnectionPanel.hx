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
    public static function request<Output>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, Output>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpRequest, ConnectionStatus, Output> {
        return requestContinually(waiting, name, Components.outMap(component, Outer), onError);
    }

    public static function requestContinually<Output>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, NextChange<HttpRequest, Output>>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpRequest, ConnectionStatus, Output> {
        return LoadingPanel.create(waiting, name, component, function (request) {
            // TODO エラーがロストする
            var ret = Connection.send(request);
            var stream = new Stream();
            ret.event.then(function (x) stream.resolve(x));
            return { event: stream, state: ret.state};
        }, onError);
    }

    public static function requestByJson<Output>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, Output>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpJsonRequest, ConnectionStatus, Output> {
        return requestByJsonContinually(waiting, name, Components.outMap(component, Outer), onError);
    }

    public static function requestByJsonContinually<Output>(
        waiting: Html -> Void,
        name,
        component: Component<Json, Void, NextChange<HttpJsonRequest,Output>>,
        onError: Dynamic -> Void
    ): PlaceHolder<HttpJsonRequest, ConnectionStatus, Output> {
        return LoadingPanel.create(waiting, name, component, function (request) {
            // TODO エラーがロストする
            var ret = Connection.sendJson(request);
            var stream = new Stream();
            ret.event.then(function (x) stream.resolve(x));
            return { event: stream, state: ret.state};
        }, onError);
    }
}
