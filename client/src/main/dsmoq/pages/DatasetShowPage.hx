package dsmoq.pages;

import dsmoq.Async;
import dsmoq.Page;
import dsmoq.models.DatasetFile;
import dsmoq.models.DatasetGuestAccessLevel;
import dsmoq.models.DatasetPermission;
import dsmoq.models.DatasetZipedFile;
import dsmoq.models.RangeSlice;
import dsmoq.models.Service;
import js.Browser;
import js.html.Element;
import hxgnd.js.JqHtml;
import hxgnd.js.jsviews.JsViews;
import hxgnd.js.jsviews.JsViews.Observable;
import hxgnd.Promise;
import hxgnd.Unit;
import hxgnd.PromiseBroker;
import hxgnd.js.Html;
import conduitbox.Navigation;
import hxgnd.js.JsTools;
import haxe.ds.Option;
using dsmoq.JQueryTools;

class DatasetShowPage {
    static inline var FILE_LIMIT = 20;
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
                    items: new Array<FileItem>(),
                    opened: false,
                    useProgress: false
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
                if (!data.item.opened || data.item.items.length > 0) {
                    if (data.item.opened) {
                        setTopMoreClickEvent(html, navigation, binding, data, id);
                        setZipClickEvent(html, navigation, data, id);
                    }
                    return;
                }
                setDatasetFiles(data, id, FILE_LIMIT, 0).then(function (_) {
                    setZipClickEvent(html, navigation, data, id);
                }, function (err) {
                    switch (err.name) {
                        case ServiceErrorType.Unauthorized:
                            navigation.fulfill(Navigation.Navigate(Page.Top));
                        case ServiceErrorType.NotFound:
                            navigation.fulfill(Navigation.Navigate(Page.Top));
                        default:
                            trace(err);
                            html.html("Network error");
                    }
                });
                setTopMoreClickEvent(html, navigation, binding, data, id);
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
    
    static function setDatasetFiles(data: Dynamic, datasetId: String, limit: Int, offset: Int): Promise<RangeSlice<DatasetFile>> {
        return Service.instance.getDatasetFiles(datasetId, { limit: limit, offset: offset }).then(function (res) {
            for (i in 0...res.results.length) {
                var file = res.results[i];
                var item = {
                    opened: false,
                    file: file,
                    items: new Array<DatasetZipedFile>(),
                    index: offset + i,
                    useProgress: false
                };
                JsViews.observable(data.item.items).insert(item);
            }
        });
    }

    static function setDatasetZippedFiles(data: Dynamic, datasetId: String, index: Int, limit: Int, offset: Int): Promise<RangeSlice<DatasetZipedFile>> {
        return Service.instance.getDatasetZippedFiles(datasetId, data.item.items[index].file.id, { limit: limit, offset: offset }).then(function (res) {
            for (file in res.results) {
                JsViews.observable(data.item.items[index].items).insert(file);
            }
        });
    }
    
    static function getIndex(obj: Dynamic): Int {
        var index = new JqHtml(obj).find("input.index").val();
        if (index == null) {
            return new JqHtml(obj).parent().find("input.index").val();
        }
        return index;
    }
    
    static function setZipClickEvent(html: Html, navigation: PromiseBroker<Navigation<Page>>, data: Dynamic, datasetId: String): Void {
        html.find(".accordion-zip-item").on("click", function (e) {
            var index: Int = getIndex(e.target);
            JsViews.observable(data.item.items[index]).setProperty("opened", !data.item.items[index].opened);
            var fileitem = data.item.items[index];
            if (!fileitem.opened || fileitem.items.length > 0) {
                if (fileitem.opened) {
                    setZipMoreClickEvent(html, navigation, data, datasetId, index);
                }
                return;
            }
            setDatasetZippedFiles(data, datasetId, index, FILE_LIMIT, 0).then(function(_) {
                setZipMoreClickEvent(html, navigation, data, datasetId, index);
            }, function(err) {
                switch (err.name) {
                    case ServiceErrorType.Unauthorized:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    case ServiceErrorType.NotFound:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    case ServiceErrorType.BadRequest:
                        var detail = cast(cast(err, ServiceError).detail, String);
                        Notification.show("error", detail);
                    default:
                        trace(err);
                        html.html("Network error");
                }
            });
        });
    }
    static function setTopMoreClickEvent(html: Html, navigation: PromiseBroker<Navigation<Page>>, binding: Observable, data: Dynamic, datasetId: String): Void {
        html.find(".more-head-item").on("click", function (_) {
            binding.setProperty("data.item.useProgress", true);
            setDatasetFiles(data, datasetId, FILE_LIMIT, data.item.items.length).then(function (_){
                binding.setProperty("data.item.useProgress", false);
                setZipClickEvent(html, navigation, data, datasetId);
            }, function (err) {
                switch (err.name) {
                    case ServiceErrorType.Unauthorized:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    case ServiceErrorType.NotFound:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    default:
                        trace(err);
                        html.html("Network error");
                }
            });
        });
    }

    static function setZipMoreClickEvent(html: Html, navigation: PromiseBroker<Navigation<Page>>, data: Dynamic, datasetId: String, index: Int): Void {
        html.find(".more-zip-item").on("click", function (_) {
            JsViews.observable(data.item.items[index]).setProperty("useProgress", true);
            setDatasetZippedFiles(data, datasetId, index, FILE_LIMIT, data.item.items[index].items.length).then(function (_) {
                JsViews.observable(data.item.items[index]).setProperty("useProgress", false);
            }, function (err) {
                switch (err.name) {
                    case ServiceErrorType.Unauthorized:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    case ServiceErrorType.NotFound:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    case ServiceErrorType.BadRequest:
                        var detail = cast(cast(err, ServiceError).detail, String);
                        Notification.show("error", detail);
                    default:
                        trace(err);
                        html.html("Network error");
                }
            });
        });
    }
}

typedef FileItem = {
    var opened: Bool;
    var items: Array<DatasetZipedFile>;
    var file: DatasetFile;
    var index: Int;
};
