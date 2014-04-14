package dsmoq.components;

import promhx.Promise;
import dsmoq.framework.Types;

import dsmoq.framework.helpers.*;
using dsmoq.framework.helpers.Components;
import dsmoq.framework.JQuery;

typedef SubmitOption = {
    url: String,
    dataType: String
}

class Clickable{
    public static function create<Input, State, Truncate>(component: Component<Input, State, Truncate>, selector = "*"): Component<Input, State, Signal>{
        function click(html){
            return Promises.tap(function(p){
                JQuery.findAll(html, selector).on("click", function(_){p.resolve(Signal);});
            });
        }
        return component.event(click);
    }

    public static function withSubmit<Input, State, Truncate>(component: Component<Input, State, Truncate>,
            clickSelector: String,
            formSelector: String,
            option: SubmitOption){
        function click(html){
            return Promises.tap(function(p){
                JQuery.findAll(html, clickSelector).on("click", function(_){
                    var jqXHR = (untyped JQuery.findAll(html, formSelector)).ajaxSubmit(option).data('jqxhr');
                    // TODO: error handling
                    jqXHR.done(function(x){p.resolve(x);});
                });
            });
        }
        return component.event(click);
    }
}
