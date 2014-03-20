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

    public static function render(id: Option<String>):Rendered<Void, Page>{
        var isNew = Core.isNone(id);
        var nextPage = switch(id){
            case Some(id): Page.DatasetRead(id);
            case None:     Page.DatasetList(None);
        }

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

        var tableActions = {
            onDelete: function(xs){
//                return Promises.value(true);
                return Promises.tap(function(p){
                    haxe.Timer.delay(function(){p.resolve(true);}, 1000);
                });
            },
            onAdd: function(xs){
                return Promises.tap(function(p){
                    if(xs[1] == ""){
                        p.resolve(false);
                    }else{
                        haxe.Timer.delay(function(){p.resolve(true);}, 1000);
                    }
                });
                return Promises.value(xs[1] != "");
            }
        }

        var aclTable:Component<Dynamic, Void, Void> = Table.editable(
                "acl-table",
                [Table.hiddenCell, Common.label, selectACL],
                [Table.hiddenCell, Common.textfield("user-name"), selectACL],
                ["", "","1"],
                tableActions, true).state(Core.ignore).event(function(_){return Promises.void();});

        var tab:Component<Dynamic, Void, Page> = Tab.base()
            .append({name: TAB_FIELD_UPLOAD, title: "Files",          component: Templates.create("DatasetEditUpload")})
            .append({name: TAB_FIELD_BASIC,  title: "Metadata",       component: Templates.create("DatasetEditBasic")})
            .append({name: TAB_FIELD_ACL,    title: "Access Control", component: Templates.create("DatasetEditAcl")})
            .toComponent()
            .decorate(putFileInputField)
            .event(function(html){
                var promise = new Promise();
                html.find("[data-link-save-upload]").on("click", function(_){
                    removeUnnecessaryField(html.find("[data-form-upload-files]"));
                    Connection.ajaxSubmit(html.find("[data-form-upload-files]"), Settings.api.datasetNewPost)
                    .then(function(_){promise.resolve(nextPage);})
                    .catchError(function(_){putFileInputField(html);});
                });
                html.find("[data-link-cancel]").on("click", function(_){ promise.resolve(nextPage);});
                return promise;
            });
        function toModel(input: Dynamic):Dynamic{
            var ret = untyped {
                componentTab: tabInfo,
                acl: [["1", "Taro","1"],["2", "Hanako", "2"],["3", "Mike", "3"]]
            };
            if(isNew){
                ret.files = [];
            }else{
                var x = Api.extractData(input);
                ret.datasetEditBasic = {
                    name: x.meta.name,
                    description: x.meta.description
                };
                ret.files = [{name: "hoge.csv"}, {name: "fuga.csv"}];
            }
            return ret;
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

        return switch(id){
            case Some(id): Common.connectionPanel("edit-view", comp, Api.datasetDetail(id));
            case None:     comp.render({});
        };
    }

    private static function putFileInputField(html: Html){
        function addFileInputField(html: Html){
            var field = JQuery.j('<input type="file" name="file[]"></input>').on("change", function(_){
                addFileInputField(html);
            });
            html.append(field);
        }

        addFileInputField(html.find("[data-form-upload-files]"));
        return html;
    }

    private static function removeUnnecessaryField(html: Html){
        html.find('input[type="file"]').filter(function(_, x){return x.value == "";}).remove();
    }
}
