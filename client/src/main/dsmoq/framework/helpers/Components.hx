package dsmoq.framework.helpers;

import dsmoq.framework.JQuery.*;
import promhx.Promise;
import promhx.Stream;
import dsmoq.framework.helpers.Streams;
import dsmoq.framework.types.ComponentFactory;
import dsmoq.framework.types.Component;
import dsmoq.framework.types.Unit;
import dsmoq.framework.types.Html;

class Components {
    public static function toComponent<Input, State, Output>(
            render: Input -> Component<State, Output>): ComponentFactory<Input, State, Output> {
        return { render: render };
    }

    public static function justHtml(html: Void -> Html): ComponentFactory<Unit, Void, Void> {
        return toComponent(function(_) {
            return {html: html(), state: Core.nop, event: new Stream() };
        });
    }

    public static function fromHtml<Input>(f: Input-> Html): ComponentFactory<Input, Void, Void> {
        return toComponent(function (x: Input) {
            return {html: f(x), state: Core.nop, event: new Stream() };
        });
    }

    public static function state<Input, State, Output>(component, f: Html -> State): ComponentFactory<Input, State, Output>{
        return toComponent(function(x: Input){
            var rendered = component.render(x);
            return {html: rendered.html, state: function(){return f(rendered.html);}, event: rendered.event};
        });
    }

    public static function event<Input, State, Output>(component, f: Html -> Stream<Output>): ComponentFactory<Input, State, Output>{
        return toComponent(function(x: Input){
            var rendered = component.render(x);
            return {html: rendered.html, state: rendered.state, event: f(rendered.html)};
        });
    }

    public static function emitInput<Input, State, Trancate>(component: ComponentFactory<Input,State,Trancate>): ComponentFactory<Input, State, Input>{
        return toComponent(function(x: Input){
            var rendered = component.render(x);
            return {html: rendered.html, state: rendered.state, event: rendered.event.then(function(_){return x;})};
        });
    }
    public static function emitState<Input, State, Trancate>(component: ComponentFactory<Input,State,Trancate>): ComponentFactory<Input, State, State>{
        return toComponent(function(x: Input){
            var rendered = component.render(x);
            return {html: rendered.html, state: rendered.state, event: rendered.event.then(function(_){return rendered.state();})};
        });
    }

    public static function decorate<Input,State,Output>( component, f:Html -> Html) :ComponentFactory<Input,State,Output>{
        return toComponent(function(a){
            return Rendereds.decorate(component.render(a), f);
        });
    }

    public static function decorateWithState<Input,State,Output>( component, f:Html -> (Void -> State) -> Html) :ComponentFactory<Input,State,Output>{
        return toComponent(function(a){
            return Rendereds.decorateWithState(component.render(a), f);
        });
    }

    public static function decorateWithInput<Input,State,Output>( component, f:Html -> Input -> Html) :ComponentFactory<Input,State,Output>{
        return toComponent(function(a){
            return Rendereds.decorate(component.render(a), f.bind(_, a));
        });
    }

    public static function inMap<Input,State,Output,Input2>(component, f:Input2 -> Input):ComponentFactory<Input2,State,Output>{
        return toComponent( function(d){
           return component.render(f(d));
        });
    }
    public static function stateMap<Input,State,Output,State2>(component, f:State -> State2):ComponentFactory<Input,State2,Output>{
        return toComponent(function(a){
            return Rendereds.stateMap(component.render(a), f);
        });
    }

    public static function outMap<Input,State,Output,Output2>(component, f:Output -> Output2):ComponentFactory<Input,State,Output2>{
        return toComponent( function(i){
            return Rendereds.eventMap(component.render(i), f);
        });
    }
    public static function merge<Input, State, Output,In1, St1, Out1, In2, St2, Out2>(
        mapForState: (Void -> St1) -> (Void -> St2) -> (Void -> State),
        mapForOutput: Stream<Out1> -> Stream<Out2> -> Stream<Output>,
        fieldName: String,
        selector: String,
        base:ComponentFactory<In1, St1, Out1>,
        component: ComponentFactory<In2, St2, Out2>
    ): ComponentFactory<Input, State, Output>{
        function render(x:Dynamic){
            var renderedBase:Component<St1,Out1>   = base.render(x);
            var targetValue = Reflect.field(x, fieldName);
            var renderedComponent= component.render(targetValue);
            JQuery.findAll(renderedBase.html, selector).append(renderedComponent.html);
            return {
                html: renderedBase.html,
                    state: mapForState(renderedBase.state, renderedComponent.state),
                    event: mapForOutput(renderedBase.event, renderedComponent.event)
            };
        }
        return toComponent(render);
    }

    public static function inject<Input, State, In1, St1, Output, In2, St2, Out2>(fieldName: String, selector: String,
            base:ComponentFactory<In1, St1, Output>, component: ComponentFactory<In2, St2, Out2>
        ): ComponentFactory<Input, State, Output>{
        return merge(injectState(fieldName), useFirst, fieldName, selector, base, component);
    }

    public static function put<Input,In1, State, Output, In2, St2>(fieldName: String, selector: String,
            base:ComponentFactory<In1, State, Output>, component: ComponentFactory<In2, St2, Output>
        ): ComponentFactory<Input, State, Output>{
        return merge(useFirst, whichever, fieldName, selector, base, component);
    }

    public static function justView<Input, In1, State, Output, In2, St2>(                                  // not typesafe
        base:ComponentFactory<In1, State, Output>, component: ComponentFactory<In2, Void, Void>,
        fieldName: String, selector: String
    ): ComponentFactory<Input, State, Output>{
        return merge(useFirst, useFirst, fieldName, selector, base, component);
    }

    public static function list<Input, State, Output>(component, f: Array<Html> -> Html): ComponentFactory<Array<Input>, Array<State>, Output>{
        return toComponent(function(xs: Array<Input>){
            return Rendereds.renderAll(component, xs, f);
        });
    }

    public static function group<Input,State,Output>(
            components: Array<ComponentFactory<Input, State, Output>>,
            f: Array<Html> -> Html
        ): ComponentFactory<Array<Input>, Array<State>, Output>{
        return {
            render: function(xs){
                if(xs.length != components.length) {
                    return  { html: j('size inconsitency'), state: Core.nop, event: new Stream()};
                }
                var rendered = [for(i in 0...components.length) components[i].render(xs[i])];
                return {
                    html: f(Rendereds.htmls(rendered)),
                    event: Streams.oneOf(Rendereds.events(rendered)),
                    state: function(){return rendered.map(function(x){return x.state();}); }
                };
            }
        };
    }

    public static function useFirst<A,B>(a: A, b: B):A{
        return a;
    }
    public static function useSecond<A,B>(a: A, b: B):B{
        return b;
    }

    public static function injectState<State>(fieldName){
        function inner<St1, St2>(base: Void -> St1, target: Void -> St2):Void -> State{
            return function(){
                var targetValue = untyped {};
                Reflect.setField(targetValue, fieldName, target());
                return Core.merge(base(), targetValue);
            };
        }
        return inner;
    }

    public static function whichever<A>(p1, p2): Stream<A> {
        return Streams.oneOf([p1, p2]);
    }
}
