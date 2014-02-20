package framework.helpers;

import promhx.Promise;
import framework.Types;
import framework.JQuery.*;

class PlaceHolders{
    public static function create<Input,State,Output>(name: String, foldable: Foldable<Input, State, Output>): PlaceHolder<Input, State, Output>{
        return withConvert(name, foldable, Core.identity);
    }

    public static function withSideEffect(name, foldable, f){
        return withConvert(name, foldable, Core.effect(f));
    }

    public static function withConvert<Input, State, Output>(name: String, foldable: Foldable<Input, State, Output>, converter: Input -> Input): PlaceHolder<Input, State, Output>{
        return {
            render: function(i){
                var p = new Promise();
                var body = divNamed(name);
                function draw(a){
                    function step(n){
                        switch(n){
                            case Inner(a): draw(a);
                            case Outer(b): p.resolve(b);
                        }
                    }
                    var rendered = foldable.render(converter(a));
                    body.empty().append(rendered.html);
                    rendered.event.then(step);
                }
                draw(i);
                return { html: body, event: p, state: Core.nop, put:draw };
            }
        };
    }

}
