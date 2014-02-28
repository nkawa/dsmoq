package components;

import framework.Types;
import framework.helpers.*;
import framework.helpers.Connection;
import framework.helpers.Promises;
import framework.JQuery;
import promhx.Promise;
import components.LoadingPanel;

class ConnectionPanel{
    public static function create<Output>(
            waiting: Html -> Void,
            name,
            component: Component<Json, Void, Output>
    ): PlaceHolder<HttpJsonRequest, ConnectionStatus, Output>{
        return requestContinually(waiting, name, Components.outMap(component, Outer));
    }
    public static function requestContinually<Output>(
            waiting: Html -> Void,
            name,
            component: Component<Json, Void, NextChange<HttpJsonRequest,Output>>
    ): PlaceHolder<HttpJsonRequest, ConnectionStatus, Output>{
        return LoadingPanel.create(waiting, name, component, Connection.sendJson);
    }
}
