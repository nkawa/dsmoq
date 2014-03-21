package pages;
import promhx.Promise;
import promhx.Stream;
import pages.Definitions;
import framework.Types;
import framework.helpers.*;
import framework.JQuery;

using components.Tab;
import components.Table;

using framework.helpers.Components;
import pages.Api;
import pages.Models;

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

        function selectACL(name){
            var accessLevel = [
                {value: "3", displayName:"Owner"},
                {value: "2", displayName:"Full Public"},
                {value: "1", displayName:"Limited Public"}
            ];
            return Common.select(name, accessLevel);
        }

        var groupCombobox = Common.select("group-combobox", Settings.groups);

        var tabInfo = if(isNew){
            {name: TAB_FIELD_UPLOAD, disables:[TAB_FIELD_BASIC, TAB_FIELD_ACL]};
        }else{
            {name: TAB_FIELD_BASIC, disables:[]};
        }

        function toAclModel(xs: RowStrings){
            if(xs.length != 3) throw "illegal size of array @ DatasetEditView";
            var level = switch(xs[2]){
                case "3": AclLevel.Owner;
                case "2": AclLevel.FullPublic;
                case "1": AclLevel.LimitedPublic;
                default: throw "illegal selection letter @ DatasetEditView";
            }
            return { id: xs[0], name: xs[1], level: level };
        }

        var tableActions: TableAction = {
            onDelete: function(xs){
                var model = toAclModel(xs);
                return Api.sendDatasetsAclDelete(Core.get(id), model.id).event.then(Some);
            },
            onAdd: function(xs){
                var model = toAclModel(xs);
                return if(model.name == ""){
                    Promises.value(None);
                }else{
                    Api.sendDatasetsAclChange(Core.get(id), model.name, model.level).event.then(function(resp){
                        return Some([resp.id, resp.name, resp.accessLevel]);
                    });
                    // TODO: resource management
                };
            },
            additional: {
                selector: 'select[name="access-level"]',
                action:function(target: Html, strings: Html -> RowStrings):Stream<Signal>{
                    var stream = new Stream();
                    function errorProcess(msg){
                        framework.Effect.global().notifyError(msg, null);
                        stream.resolve(Signal);
                    }
                    target.on("change", function(_){
                        var model = toAclModel(strings(JQuery.self()));
                        Api.sendDatasetsAclChange(Core.get(id), model.id, model.level).event.then(function(_){
                            stream.resolve(Signal);
                        }).catchError(errorProcess);
                    });
                    return stream;
                }
            }
        }

        var aclTable:Component<Dynamic, Void, Void> = Table.editable(
                "acl-table",
                [Table.hiddenCell, Common.label, selectACL("access-level")],
                [Table.hiddenCell, groupCombobox, selectACL("access-level-input")],
                ["", "","1"],
                tableActions, true).state(Core.ignore).event(function(_){return Promises.void();});

        var tab:Component<Dynamic, Void, Page> = Tab.base()
            .append({name: TAB_FIELD_UPLOAD, title: "Files",          component: Templates.create("DatasetEditUpload")})
            .append({name: TAB_FIELD_BASIC,  title: "Metadata",       component: Templates.create("DatasetEditBasic")})
            .append({name: TAB_FIELD_ACL,    title: "Access Control", component: Templates.create("DatasetEditAcl")})
            .toComponent()
            .decorate(putFileInputField)
            .decorate(function(html){
                return JQuery.j('<div class="text-right"><a class="btn btn-primary" data-link-cancel>Back</a></div>').add(html);
            })
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
                acl: [],
                defaultAC: "1"
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
                {value: "1", displayName:"Limited public (ignore download)"},
                {value: "2", displayName:"Full public (allow download)"}
            ];
            function toDefaultAccess(str: String){
                return switch(str){
                    case "0": DefaultLevel.Deny;
                    case "1": DefaultLevel.LimitedPublic;
                    case "2": DefaultLevel.FullPublic;
                    default: throw "illegal selection letter @ DatasetEditView";
                }
            }
            var stream = new Stream();
            function errorProcess(msg){
                framework.Effect.global().notifyError(msg, null);
                stream.resolve(Signal);
            }
            Common.withRequest(Common.radio("defaultAccessLevel", accessLevel), "click", function(stateStream){
                stateStream.then(function(state){
                    Api.sendDatasetsDefaultAccess(Core.get(id), toDefaultAccess(state)).event.then(function(_){
                        stream.resolve(Signal);
                    }).catchError(errorProcess);
                });
                return stream;
            }, "input").state(Core.ignore);
        }
        var files = Components.list(Templates.create("UploadFileEdit"), JQuery.fromArray).state(Core.ignore);
        var comp = tab.justView(aclTable, "acl", "[data-component-acl-table]")
            .justView(defaultACRadio, "defaultAC", "[data-component-acl-default]")
            .justView(files, "files", "[data-component-files]")
            .inMap(toModel);

        return switch(id){
            case Some(id): Common.connectionPanel("edit-view", comp, Api.datasetDetail(id));
            case None:     comp.render({});
        };
    }

    private static function layout(html){
        return JQuery.j('<div><a class="btn btn-primary">Done</a></div>').append(html);
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
