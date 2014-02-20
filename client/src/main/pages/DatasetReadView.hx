package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.JQuery.*;

import components.Clickable;

class DatasetReadView{
    public static function render(id: String){
        function button(id:String){
            return Clickable.clickable(function(page:Page){
                return div().text('Your id is: $id. Click to go to ' + Std.string(page));
            });
        }
        return button(id).render(DashBoard);
    }
}
