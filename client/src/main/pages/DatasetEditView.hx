package pages;
import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.helpers.*;
import framework.JQuery;

using components.Tab;
import components.Table;

using framework.helpers.Components;

private typedef DatasetEditViewModel = {
    name: String,
    description: String
}

class DatasetEditView{
    static inline var TAB_FIELD_UPLOAD = "datasetEditUpload";
    static inline var TAB_FIELD_BASIC  = "datasetEditBasic";
    static inline var TAB_FIELD_ACL    = "datasetEditAcl";

    public static function render(id: String, isNew: Bool):Rendered<Void, Page>{
        var selectACL = {
            var accessLevel = [
                {value: "1", displayName:"Owner"},
                {value: "2", displayName:"Full Read"},
                {value: "3", displayName:"Limited Read"}
            ];
            Common.select("access-level", accessLevel);
        }

        var tabInfo = if(isNew){
            {name: TAB_FIELD_UPLOAD, disables:[TAB_FIELD_BASIC, TAB_FIELD_ACL]};
        }else{
            {name: TAB_FIELD_BASIC, disables:[]};
        }

        var aclTable:Component<Dynamic, Void, Void> = Table.editable(
                "acl-table",
                [Common.label, selectACL],
                [Common.textfield("user-name"), selectACL],
                ["","1"]).state(Core.ignore).event(function(_){return Promises.void();});

        var tab:Component<Dynamic, Void, Page> = Tab.base()
            .append({name: TAB_FIELD_UPLOAD, title: "Files",          component: Templates.create("DatasetEditUpload")})
            .append({name: TAB_FIELD_BASIC,  title: "Metadata",       component: Templates.create("DatasetEditBasic")})
            .append({name: TAB_FIELD_ACL,    title: "Access Control", component: Templates.create("DatasetEditAcl")})
            .toComponent()
            .event(function(html){
                var promise = new Promise();
                html.find("[data-link-cancel]").on("click", function(_){ promise.resolve(Page.DatasetRead(id));});
                return promise;
            });
        function toModel(input: Dynamic):Dynamic{
            var x = Api.extractData(input);
            return {
                datasetEditBasic: {
                    name: x.meta.name,
                    description: x.meta.description,
                },
                componentTab: tabInfo,
                acl: [["Taro","1"],["Hanako", "2"],["Mike", "3"]],
                files: [{name: "hoge.csv"}, {name: "fuga.csv"}]
            };
        }
        var defaultACRadio = {
            var accessLevel = [
                {value: "0", displayName:"Deny"},
                {value: "1", displayName:"limited public (ignore download)"},
                {value: "2", displayName:"full public (allow download)"}
            ];
            Common.radio("defaultAccessLevel", accessLevel);
        }
        var files = Components.list(Templates.create("UploadFileEdit"), JQuery.fromArray).state(Core.ignore);
        var comp = tab.justView(aclTable, "acl", "[data-component-acl-table]")
            .justView(defaultACRadio, "defaultAC", "[data-component-defaultAC-select]")
            .justView(files, "files", "[data-component-files]")
            .inMap(toModel);

        return Common.connectionPanel("edit-view", comp, Api.datasetDetail(id));
    }
}
