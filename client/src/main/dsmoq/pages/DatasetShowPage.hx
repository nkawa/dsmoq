package dsmoq.pages;

import dsmoq.Async;
import dsmoq.Page;
import dsmoq.models.ApiStatus;
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
                root: {
                    name: res.filesCount + " files",
                    files: new Array<FileItem>(),
                    opened: res.filesCount > 0,
                    useProgress: res.filesCount > 0
                },
                attributes: res.meta.attributes,
                license: res.meta.license,
                isPrivate: Type.enumEq(res.defaultAccessLevel, DatasetGuestAccessLevel.Deny),
                canEdit: Type.enumEq(res.permission, DatasetPermission.Write),
                canDownload: switch (res.permission) {
                    case DatasetPermission.Write, DatasetPermission.Read: true;
                    case _: false;
                },
                appUrl: res.appUrl,
				app: res.app,
                accessCount: res.accessCount,
                filesCount: res.filesCount,
                fileLimit: res.fileLimit
            };
            binding.setProperty("data", data);
			binding.setProperty("data.appUrl", data.appUrl);

            html.find("#dataset-edit").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(Page.DatasetEdit(id)));
            });

            html.find("#dataset-delete").createEventStream("click").flatMap(function (_) {
                return JsTools.confirm("Are you sure you want to delete this dataset?");
            }).flatMap(function (_) {
                return Service.instance.deleteDeataset(id);
            }).then(function (_) {
                // TODO 削除対象データセット閲覧履歴（このページ）をHistoryから消す
                navigation.fulfill(Navigation.Navigate(Page.DatasetList(1, "")));
            });
            
            html.find("#dataset-copy").createEventStream("click").flatMap(function (_) {
                return JsTools.confirm("Are you sure you want to copy this dataset?");
            }).flatMap(function (_) {
                return Service.instance.copyDataset(id);
            }).then(function(x) {
                navigation.fulfill(Navigation.Navigate(Page.DatasetShow(x.datasetId)));
            });

            if (data.filesCount > 0) {
                // ファイル一覧の表示開閉切替
                html.find(".accordion-head-item").on("click", function (_) {
                    binding.setProperty("data.root.opened", !data.root.opened);
                    if (data.root.opened) {
                        setTopMoreClickEvent(html, navigation, binding, data, id);
                        setZipClickEvent(html, navigation, data, id);
                        for (i in 0...data.root.files.length) {
                            var fileitem = data.root.files[i];
                            if (fileitem.opened && fileitem.zippedFiles.length < fileitem.file.zipCount) {
                                setZipMoreClickEvent(html, navigation, data, id, i);
                            }
                        }
                    }
                });
                setDatasetFiles(data, id, data.fileLimit, 0).then(
                    function(files) {
                        JsViews.observable(data.root.files).refresh(files);
                        binding.setProperty("data.root.useProgress", false);
                        setTopMoreClickEvent(html, navigation, binding, data, id);
                        setZipClickEvent(html, navigation, data, id);
                    },
                    function() {
                        binding.setProperty("data.root.useProgress", false);
                    }
                );
            }
        }, function (err: Dynamic) {
            switch (err.status) {
                case 403: // Forbidden
                    switch (err.responseJSON.status) {
                        case ApiStatus.AccessDenied:
                            navigation.fulfill(Navigation.Navigate(Page.Top));
                        case ApiStatus.Unauthorized:
                            navigation.fulfill(Navigation.Navigate(Page.Top));
                    }
                case 404: // NotFound
                    switch (err.responseJSON.status) {
                        case ApiStatus.NotFound:
                            navigation.fulfill(Navigation.Navigate(Page.Top));
                        case ApiStatus.IllegalArgument:
                            navigation.fulfill(Navigation.Navigate(Page.Top));
                    }
                case _: // その他(500系など)
                    html.html("Network error");
            }
        });

        return navigation.promise;
    }
   
    /**
     * データセットのファイル一覧を取得し、画面に設定する。
     *
     * @param data 画面で保持するbindingのデータ
     * @param datasetId データセットID
     * @param limit データセットのファイルの取得Limit
     * @param offset データセットのファイルの取得位置
     * @return データセットのファイル一覧取得のPromise
     */
    static function setDatasetFiles(data: Dynamic, datasetId: String, limit: Int, offset: Int): Promise<Array<FileItem>> {
        return Service.instance.getDatasetFiles(datasetId, { limit: limit, offset: offset }).map(function (res) {
            var i = 0;
            return res.results.map(function (file) {
                var index = offset + i;
                i++;
                return datasetFileToFileItem(file, index);
            });
        });
    }

    static function datasetFileToFileItem(datasetFile: DatasetFile, index: Int): FileItem {
        return {
            opened: false,
            file: datasetFile,
            zippedFiles: new Array<DatasetZipedFile>(),
            index: index,
            useProgress: false
        };
    }

    /**
     * データセットのZIP内ファイル一覧を取得し、画面に設定する。
     *
     * @param data 画面で保持するbindingのデータ
     * @param datasetId データセットID
     * @param index データセットのファイル中の、ZIPファイルのindex
     * @param limit データセットのファイルの取得Limit
     * @param offset データセットのファイルの取得位置
     * @return データセットのZIP内ファイル一覧取得のPromise
     */
    static function setDatasetZippedFiles(data: Dynamic, datasetId: String, index: Int, limit: Int, offset: Int): Promise<RangeSlice<DatasetZipedFile>> {
        JsViews.observable(data.root.files[index]).setProperty("useProgress", true);
        return Service.instance.getDatasetZippedFiles(datasetId, data.root.files[index].file.id, { limit: limit, offset: offset }).then(function (res) {
            JsViews.observable(data.root.files[index]).setProperty("useProgress", false);
            for (file in res.results) {
                JsViews.observable(data.root.files[index].zippedFiles).insert(file);
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
    
    /**
     * ZIPファイルを展開したときのイベントを設定する。
     *
     * @param html
     * @param navigation
     * @param data
     * @param datasetId データセットID
     */
    static function setZipClickEvent(html: Html, navigation: PromiseBroker<Navigation<Page>>, data: Dynamic, datasetId: String): Void {
        html.find(".accordion-zip-item").on("click", function (e) {
            var index: Int = getIndex(e.target);
            JsViews.observable(data.root.files[index]).setProperty("opened", !data.root.files[index].opened);
            var fileitem = data.root.files[index];
            if (!fileitem.opened || fileitem.zippedFiles.length > 0) {
                if (fileitem.opened && fileitem.zippedFiles.length < fileitem.file.zipCount) {
                    setZipMoreClickEvent(html, navigation, data, datasetId, index);
                }
                return;
            }
            setDatasetZippedFiles(data, datasetId, index, data.fileLimit, 0).then(function(_) {
                setZipMoreClickEvent(html, navigation, data, datasetId, index);
            }, function(err: Dynamic) {
                switch (err.status) {
                    case 400: // BadRequest
                        switch (err.responseJSON.status) {
                            case ApiStatus.BadRequest:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                            case _:
                                html.html(err.responseJSON.status);
                        }
                    case 403: // Forbidden
                        switch (err.responseJSON.status) {
                            case ApiStatus.AccessDenied:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                            case ApiStatus.Unauthorized:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                        }
                    case 404: // NotFound
                        switch (err.responseJSON.status) {
                            case ApiStatus.NotFound:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                        }
                    case _: // その他(500系など)
                        html.html("Network error");
                }
            });
        });
    }

    /**
     * ファイルのmore file表示をクリックしたときのイベントを設定する。
     *
     * @param html
     * @param navigation
     * @param binding
     * @param data
     * @param datasetId データセットID
     */
    static function setTopMoreClickEvent(html: Html, navigation: PromiseBroker<Navigation<Page>>, binding: Observable, data: Dynamic, datasetId: String): Void {
        html.find(".more-head-item").on("click", function (_) {
            binding.setProperty("data.root.useProgress", true);
            setDatasetFiles(data, datasetId, data.fileLimit, data.root.files.length).then(function (files){
                binding.setProperty("data.root.useProgress", false);
                JsViews.observable(data.root.files).insert(files);
                setTopMoreClickEvent(html, navigation, binding, data, datasetId);
                setZipClickEvent(html, navigation, data, datasetId);
            }, function (err: Dynamic) {
                switch (err.status) {
                    case 403: // Forbidden
                        switch (err.responseJSON.status) {
                            case ApiStatus.AccessDenied:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                            case ApiStatus.Unauthorized:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                        }
                    case 404: // NotFound
                        switch (err.responseJSON.status) {
                            case ApiStatus.NotFound:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                        }
                    case _: // その他(500系など)
                        html.html("Network error");
                }
            });
        });
    }

    /**
     * ZIPファイルのmore file表示をクリックしたときのイベントを設定する。
     *
     * @param html
     * @param navigation
     * @param binding
     * @param data
     * @param datasetId データセットID
     * @param index データセットのファイル中の、ZIPファイルのindex
     */
    static function setZipMoreClickEvent(html: Html, navigation: PromiseBroker<Navigation<Page>>, data: Dynamic, datasetId: String, index: Int): Void {
        html.find(".more-zip-item").on("click", function (_) {
            JsViews.observable(data.root.files[index]).setProperty("useProgress", true);
            setDatasetZippedFiles(data, datasetId, index, data.fileLimit, data.root.files[index].zippedFiles.length).then(function (_) {
                JsViews.observable(data.root.files[index]).setProperty("useProgress", false);
                setZipMoreClickEvent(html, navigation, data, datasetId, index);
            }, function (err: Dynamic) {
                switch (err.status) {
                    case 400: // BadRequest
                        switch (err.responseJSON.status) {
                            case ApiStatus.BadRequest:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                            case _:
                                html.html(err.responseJSON.status);
                        }
                    case 403: // Forbidden
                        switch (err.responseJSON.status) {
                            case ApiStatus.AccessDenied:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                            case ApiStatus.Unauthorized:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                        }
                    case 404: // NotFound
                        switch (err.responseJSON.status) {
                            case ApiStatus.NotFound:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                        }
                    case _: // その他(500系など)
                        html.html("Network error");
                }
            });
        });
    }
}

typedef FileItem = {
    var opened: Bool;
    var zippedFiles: Array<DatasetZipedFile>;
    var file: DatasetFile;
    var index: Int;
    var useProgress: Bool;
};
