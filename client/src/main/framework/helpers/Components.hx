package framework.helpers;

import framework.Types;
import framework.JQuery.*;


class Components{
    public static function toComponent<Input, State, Output>(render: Input -> Rendered<State, Output>){
        return { render: render };
    }

    static function decorate<Input,State,Output>(
        component: Component<Input,State,Output>, f:Html -> Html
    ) :Component<Input,State,Output>{
        return toComponent(function(a){
            return Rendereds.decorate(component.render(a), f);
        });
    }
 
    public static function inMap<Input,State,Output,Input2>(
        component: Component<Input,State,Output>, f:Input2 -> Input
    ):Component<Input2,State,Output>{
        return toComponent( function(d){
           return component.render(f(d));
        });
    }
    static function stateMap<Input,State,Output,State2>(
        component: Component<Input,State,Output>, f:State -> State2
    ):Component<Input,State2,Output>{
        return null;
    }

    public static function outMap<Input,State,Output,Output2>(
        component: Component<Input,State,Output>, f:Output -> Output2
    ):Component<Input,State,Output2>{
        return toComponent( function(i){ 
            return Rendereds.eventMap(component.render(i), f);
        });
    }

    public static function group<Input,State>(
        components: Array<Component<Input, State, Void>>,
        f: Array<Html> -> Html
    ): Component<Array<Input>, Array<State>, Void>{
        return {
            render: function(xs){
                if(xs.length != components.length) {
                    return  { html: j('size inconsitency'), state: Core.nop, event: Promises.void()};
                }
                var rendered = [for(i in 0...components.length) components[i].render(xs[i])];
                return {
                    html: f(rendered.map(function(x){return x.html;})),
                    event: Promises.void(),
                    state: function(){return rendered.map(function(x){return x.state();}); }
                };
            }
        };
    }

    public static function list<Input, State, Output>(component: Component<Input, State, Output>, f: Array<Html> -> Html): Component<Array<Input>, Array<State>, Output>{
        return toComponent(function(xs: Array<Input>){
            return Rendereds.renderAll(component, xs, f);
        });
    }
}
