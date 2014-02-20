package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.JQuery.*;

import components.Clickable;
import framework.helpers.*;

class DashBoardView{
    public static function render(){
        function button(){
            return Clickable.clickable(function(page:Page){
                return div().text('Your are in Dashboard. Click to go to ' + Std.string(page));
            });
        }
        return button().render(DatasetRead("1"));
    }
}
