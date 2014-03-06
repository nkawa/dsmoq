package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.helpers.*;
import framework.JQuery.*;

import components.Clickable;
using framework.helpers.Components;

class DatasetReadView{
    public static function render(id: String):Rendered<Void, Page>{
        function button(id:String){
            return Clickable.create(
                Components.fromHtml(function(page:Page){
                    return div().text('Your id is: $id. Click to go to ' + Std.string(page));
                })
            ).emitInput();
        }
        return button(id).render(DatasetList);
    }
}
