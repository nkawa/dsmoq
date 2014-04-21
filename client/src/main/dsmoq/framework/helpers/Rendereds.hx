package dsmoq.framework.helpers;

import dsmoq.framework.JQuery.*;
import dsmoq.framework.types.Component;
import dsmoq.framework.types.Html;
import dsmoq.framework.types.ComponentFactory;

class Rendereds{
    public static function oneOfEvent(components){
        return Streams.oneOf(components.map(function(c){return c.event;}));
    }

    public static function stateMap<State, Output, State2>( r: Component<State,Output>, f: State -> State2):Component<State2, Output> {
        return {
            html: r.html,
            state: function(){return f(r.state());},
            event: r.event
        };
    }

    public static function eventMap<State, Output, Output2>( r: Component<State,Output>, f: Output -> Output2):Component<State, Output2> {
        return {
            html: r.html,
            state: r.state,
            event: r.event.then(f)
        };
    }
    public static function decorate<State, Output>( r: Component<State,Output>, f: Html -> Html):Component<State, Output> {
        return {
            html: f(r.html),
            state: r.state,
            event: r.event
        };
    }
    public static function decorateWithState<State, Output>( r: Component<State,Output>, f: Html -> (Void -> State) -> Html):Component<State, Output> {
        return {
            html: f(r.html, r.state),
            state: r.state,
            event: r.event
        };
    }
    public static function htmls<State,Output>(rendereds: Array<Component<State, Output>>){
        return rendereds.map(function(x){return x.html;});
    }

    public static function events<State,Output>(rendereds: Array<Component<State, Output>>){
        return rendereds.map(function(x){return x.event;});
    }

    public static function states<State,Output>(rendereds: Array<Component<State, Output>>){
        var array = rendereds.map(function(x){return x.state;});
        return Core.toState(array);
    }
    public static function renderAll<Input,State,Output>(
        component:ComponentFactory<Input,State,Output>,
        inputs: Array<Input>,
        f: Array<Html> -> Html): Component<Array<State>, Output>
    {
        var rendered = inputs.map(component.render);
        return {html: f(htmls(rendered)), state: states(rendered), event:Streams.oneOf(events(rendered))};
    }
}
