package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.helpers.*;
import framework.JQuery.*;

using components.Tab;

using framework.helpers.Components;

class DatasetEditView{
    public static function render(id: String):Rendered<Void, Page>{
        var tab = Tab.base()
            .append({name: "datasetEditUpload", title: "Files",          component: Templates.create("DatasetEditUpload")})
            .append({name: "datasetEditBasic",  title: "Metadata",       component: Templates.create("DatasetEditBasic")})
            .append({name: "datasetEditAcl",    title: "Access Control", component: Templates.create("DatasetEditAcl")})
            .toComponent()
            .event(function(html){
                var promise = new Promise();
                html.find("[data-link-cancel]").on("click", function(_){ promise.resolve(Page.DatasetRead(id));});
                return promise;
            });

        return tab.render({componentTabName: "datasetEditUpload"});
    }
}
