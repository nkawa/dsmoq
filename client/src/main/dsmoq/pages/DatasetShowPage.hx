package dsmoq.pages;

import dsmoq.Async;
import dsmoq.models.DatasetGuestAccessLevel;
import dsmoq.models.DatasetPermission;
import dsmoq.models.Service;
import js.Browser;
import js.html.Element;
import hxgnd.js.JqHtml;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.Unit;
import hxgnd.PromiseBroker;
import hxgnd.js.Html;
import conduitbox.Navigation;
import hxgnd.js.JsTools;
import haxe.ds.Option;
using dsmoq.JQueryTools;

class DatasetShowPage {
    public static function render(html: Html, onClose: Promise<Unit>, id: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();
        var binding = JsViews.observable({ data: Async.Pending });
        View.getTemplate("dataset/show").link(html, binding.data());

        Service.instance.getDataset(id).then(function (res) {
            var data = {
                name: res.meta.name,
                description: res.meta.description,
                primaryImage: res.primaryImage,
                featuredImage: res.featuredImage,
                ownerships: res.ownerships.filter(function (x) return Type.enumEq(x.accessLevel, DatasetPermission.Write)),
                item: {
                    name: res.filesCount + " files",
                    items: [],
                    opened: false
                },
                attributes: res.meta.attributes,
                license: res.meta.license,
                isPrivate: Type.enumEq(res.defaultAccessLevel, DatasetGuestAccessLevel.Deny),
                canEdit: Type.enumEq(res.permission, DatasetPermission.Write),
                canDownload: switch (res.permission) {
                    case DatasetPermission.Write, DatasetPermission.Read: true;
                    case _: false;
                },
                accessCount: res.accessCount,
                filesCount: res.filesCount
            };
            binding.setProperty("data", data);

            html.find("#dataset-edit").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(Page.DatasetEdit(id)));
            });

            html.find("#dataset-delete").createEventStream("click").flatMap(function (_) {
                return JsTools.confirm("Are you sure you want to delete this dataset?");
            }).flatMap(function (_) {
                return Service.instance.deleteDeataset(id);
            }).then(function (_) {
                // TODO 削除対象データセット閲覧履歴（このページ）をHistoryから消す
                navigation.fulfill(Navigation.Navigate(Page.DatasetList(1, "", new Array<{type: String, item: Dynamic}>())));
            });
            
            html.find("#dataset-copy").createEventStream("click").flatMap(function (_) {
                return JsTools.confirm("Are you sure you want to copy this dataset?");
            }).flatMap(function (_) {
                return Service.instance.copyDataset(id);
            }).then(function(x) {
                navigation.fulfill(Navigation.Navigate(Page.DatasetShow(x.datasetId)));
            });
            
            html.find(".accordion-head-item").on("click", function (_) {
                binding.setProperty("data.item.opened", !data.item.opened);
                if (data.item.opened && data.item.items.length == 0) {
                    getDatasetFiles(html, data, id, 5, 0);
                }
                html.find(".more-head-item").on("click", function (_) {
                    trace("hoge");
                    getDatasetFiles(html, data, id, 5, data.item.items.length);
                });
            });
            
        }, function (err) {
            switch (err.name) {
                case ServiceErrorType.Unauthorized:
                    html.html("Permission denied");
                case ServiceErrorType.NotFound:
                    html.html("Not found");
                default:
                    html.html("Network error");
            }
        });

        return navigation.promise;
    }
    
    static function getDatasetFiles(html: Html, data: Dynamic, datasetId: String, limit: Int, offset: Int): Void {
        Service.instance.getDatasetFiles(datasetId, { limit: limit, offset: offset }).then(function (res) {
            for (file in res.results) {
                var item = {
                    opened: false,
                    file: file,
                    items: []
                };
                JsViews.observable(data.item.items).insert(item);
            }
        }, function (err) {
            switch (err.name) {
                case ServiceErrorType.Unauthorized:
                    html.html("Permission denied");
                case ServiceErrorType.NotFound:
                    html.html("Not found");
                default:
                    trace(err);
                    html.html("Network error");
            }
        });
    }
}
