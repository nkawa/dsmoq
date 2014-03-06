package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.JQuery;

import components.Clickable;
import framework.helpers.*;
using framework.helpers.Components;

class DashBoardView{
    public static function render(){
         function button(){
            return Clickable.create(
                Components.fromHtml(function(page:Page){
                    return JQuery.div().text('Your are in Dashboard. Click to go to ' + Std.string(page));
                })
            ).emitInput();
         }
        return button().render(DatasetRead("1"));
    }
}
