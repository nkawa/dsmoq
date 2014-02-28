package components;
import promhx.Promise;
import framework.Types;

import framework.helpers.*;
using framework.helpers.Components;
import framework.JQuery;

class Clickable{
    public static function create<Input, State, Truncate>(component: Component<Input, State, Truncate>, selector = "*"): Component<Input, State, Signal>{
        function click(html){
            return Promises.tap(function(p){
                JQuery.findAll(html, selector).on("click", function(_){p.resolve(Signal);});
            });
        }
        return component.event(click);
    }
}
