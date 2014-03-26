package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.helpers.*;
import framework.JQuery;
import components.LoadingPanel;
import pages.Models;

using components.Tab;

using framework.helpers.Components;
import components.Table;

private typedef DatasetViewModel = {
    name: String,
    description: String,
    canDownload: Bool,
    canEdit: Bool,
    license: String
}

class DatasetReadView{
    public static function render(id: String):Rendered<Void, Page>{
        function toModel(input: Dynamic): Dynamic /* DatasetViewModel */{
            var data = Api.extractData(input);

            function licenseString(i: Int){
                return switch(i){
                    case 1: "Creative Commons Attribution";
                    default: "License Not Specified";
                }
            }
            function fileViewModel(file){
                return {
                    name: file.name,
                    description: file.description,
                    size: file.size,
                    uploadedBy: Common.displayStringForUser(file.updatedBy)
                };
            }
            function aclViewModel(owner:Person){
                return [Common.displayStringForUser(owner), Common.displayStringForAccess(owner.accessLevel)];
            }
            return {
                name: data.meta.name,
                description: data.meta.description,
                canDownload: (data.permission >= 1),
                canEdit: (data.permission >= 2),
                license: licenseString(data.meta.license),
                files: data.files.map(fileViewModel),
                acls: data.ownerships.map(aclViewModel)
            };
        }
        var files = Components.list(Templates.create("UploadFileRead"), JQuery.fromArray).state(Core.ignore);
        var aclTable = Table.create("acl-table", [Common.label, Common.label], ["#", "name", "access"]).state(Core.ignore);

        var main = Templates.create("DatasetReadMain")
            .justView(files, "files", "[data-component-files]")
            .justView(aclTable, "acls", "[data-component-acl-table]")
            .inMap(toModel)
            .event(function(html){
                var promise = new Promise();
                html.find("[data-link-edit]").on("click", function(_){ promise.resolve(Page.DatasetEdit(id));});
                return promise;
            });

        return Common.connectionPanel("read-view", main, Api.datasetDetail(id));
    }
}
