package dsmoq.pages;

import promhx.Promise;
import dsmoq.pages.Definitions;
import dsmoq.framework.Types;
import dsmoq.framework.helpers.*;
import dsmoq.framework.JQuery;
import dsmoq.components.LoadingPanel;
import dsmoq.pages.Models;

using dsmoq.components.Tab;

using dsmoq.framework.helpers.Components;
import dsmoq.components.Table;

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
            function aclViewModel(owner:Person){
                return [Common.displayStringForUser(owner), Common.displayStringForAccess(owner.accessLevel)];
            }
            return {
                name: data.meta.name,
                description: data.meta.description,
                canEdit: (data.permission >= 3),
                license: licenseString(data.meta.license),
                files: data.files.map(Common.fileViewModel.bind(_, data.permission >= 2)),
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
