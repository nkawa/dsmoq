package components;
import promhx.Promise;
import framework.Types;

import framework.helpers.*;

class Clickable{
    public static function clickable<Input>(
        f: Input -> Html, selector = "") : Component<Input, Void, Input>
    {
        function render(a: Input){
            var p = new Promise<Input>();
            var html = f(a);
            (selector == "" ? html : html.find(selector))
                .on("click", function(_){p.resolve(a);});
            return { html: html, event: p, state: Core.nop };
        }
        return { render: render };
  }
}
