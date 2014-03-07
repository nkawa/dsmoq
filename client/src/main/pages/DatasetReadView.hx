package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.helpers.*;
import framework.JQuery.*;

using components.Tab;

using framework.helpers.Components;

class DatasetReadView{
    public static function render(id: String):Rendered<Void, Page>{
        var main = Templates.create("DatasetReadMain")
            .event(function(html){
                var promise = new Promise();
                html.find("[data-link-edit]").on("click", function(_){ promise.resolve(Page.DatasetEdit(id));});
                return promise;
            });

        return main.render(null);
    }
}
