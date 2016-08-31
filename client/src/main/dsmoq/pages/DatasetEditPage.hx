package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.CKEditor;
import dsmoq.CSV;
import dsmoq.View;
import dsmoq.models.ApiStatus;
import dsmoq.models.DatasetApp;
import dsmoq.models.DatasetFile;
import dsmoq.models.DatasetImage;
import dsmoq.models.DatasetPermission;
import dsmoq.models.GroupRole;
import dsmoq.models.Image;
import dsmoq.models.Profile;
import dsmoq.models.RangeSlice;
import dsmoq.models.Service;
import dsmoq.models.SuggestedOwner;
import dsmoq.pages.datas.DatasetEdit;
import dsmoq.views.AutoComplete;
import dsmoq.views.AutoComplete;
import dsmoq.views.ViewTools;
import haxe.Json;
import haxe.Resource;
import haxe.ds.Option;
import hxgnd.ArrayTools;
import hxgnd.Error;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Result;
import hxgnd.Unit;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.JqHtml;
import hxgnd.js.JsTools;
import hxgnd.js.jsviews.JsViews;
import js.Browser;
import js.Lib;
import js.bootstrap.BootstrapButton;
import js.html.Element;
import js.html.Event;
import js.html.EventTarget;
import js.html.FileReader;
import js.html.InputElement;
import js.html.OptionElement;

using hxgnd.OptionTools;

class DatasetEditPage {
    inline static var OwnerCandicateSize = 5;
    inline static var ImageCandicateSize = 10;
    inline static var AppCandicateSize = 9;

    public static function render(root: Html, onClose: Promise<Unit>, id: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        function setAttributeTypeahead(root: JqHtml) {
            AutoComplete.initialize(root.find(".attribute-typeahead"), {
                url: function (query: String) {
                    var d = Json.stringify({query: StringTools.urlEncode(query)});
                    return '/api/suggests/attributes?d=${d}';
                },
                filter: function (data: Dynamic) {
                    return if (data.status == "OK" && Std.is(data.data, Array)) {
                        Result.Success(data.data);
                    } else {
                        Result.Failure(new Error("Network Error"));
                    }
                },
                template: {
                    suggestion: function (x) {
                        return '<div>${x}</div>';
                    }
                }
            });
        }

        function removeAttributeTypeahead(root: JqHtml) {
            AutoComplete.destroy(root.find(".attribute-typeahead"));
        }

        var rootBinding = JsViews.observable({ data: dsmoq.Async.Pending });
        View.getTemplate("dataset/edit").link(root, rootBinding.data());

        var promise = Service.instance.getDataset(id).flatMap(function (ds) {
            return switch (ds.permission) {
                case DatasetPermission.Write:
                    Promise.fulfilled(ds);
                case _:
                    Promise.rejected(new ServiceError("", ServiceErrorType.Unauthorized));
            }
        })
        .thenError(function (err: Dynamic) {
            root.html(err.responseJSON.status);
        });

        promise.then(function (x) {
            var data: DatasetEdit = {
                myself: Service.instance.profile,
                licenses: Service.instance.licenses,
                dataset: {
                    id: x.id,
                    meta: x.meta,
                    files: {
                        index: 0,
                        limit: x.fileLimit,
                        total: x.filesCount,
                        items: new Array<DatasetFile>(),
                        pages: calcPages(x.filesCount, x.fileLimit),
                        useProgress: false
                    },
                    updatedFiles: [],
                    ownerships: x.ownerships,
                    defaultAccessLevel: x.defaultAccessLevel,
                    primaryImage: x.primaryImage,
                    featuredImage: x.featuredImage,
                    localState: x.localState,
                    s3State: x.s3State,
                    primaryApp: null,
                    errors: {
                        meta: {
                            name: "",
                            description: "",
                            license: "",
                            attributes: "",
                        },
                        icon: "",
                        files: {
                            images: "",
                        },
                        ownerships: { },
                    }
                },
                owners: Async.Pending
            };
            rootBinding.setProperty("data", data);

            var binding = JsViews.observable(rootBinding.data().data);
            
            // CKEditor setting
            var editor = CKEditor.replace("description");
            editor.setData(data.dataset.meta.description);
            editor.on("change", function(evt) {
                var text = editor.getData(false);
                binding.setProperty('dataset.meta.description', text);
            });
            
            onClose.then(function(_) {
                editor.destroy();
            } );

            editor.on("on-click-dialog-button", function(evt) {
                JQuery._(".cke_dialog_background_cover").css("z-index", "1000");
                JQuery._(".cke_dialog ").css("z-index", "1010");
                showSelectImageDialog(id, binding).then(function(image) {
                    JQuery._('.url-text input[type="text"]').val(image.url);
                    editor.fireOnce("on-close-dialog", image);
                });
            } );
            
            setAttributeTypeahead(root);
            AutoComplete.initialize(root.find("#dataset-owner-typeahead"), {
                url: function (query: String) {
                    var d = Json.stringify({query: StringTools.urlEncode(query)});
                    return '/api/suggests/users_and_groups?d=${d}';
                },
                path: "name",
                filter: function (data: Dynamic) {
                    return if (data.status == "OK" && Std.is(data.data, Array)) {
                        Result.Success(data.data);
                    } else {
                        Result.Failure(new Error("Network Error"));
                    }
                },
                template: {
                    suggestion: function (x) {
                        return '<div>${x.name}</div>';
                    }
                }
            });

            root.find("#dataset-finish-editing").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(Page.DatasetShow(id)));
            });

            // basics
            root.find("#dataset-attribute-add").on("click", function (_) {
                removeAttributeTypeahead(root);
                var attrs = JsViews.observable(data.dataset.meta.attributes);
                attrs.insert({ name: "", value:"" });
                setAttributeTypeahead(root);
            });
            root.on("click", ".dataset-attribute-remove", function (e) {
                removeAttributeTypeahead(root);
                var index = new JqHtml(e.target).data("value");
                // undefined が取れてくる場合、e.targetはspanになっている。
                if (index == null) {
                    index = new JqHtml(e.target).parent().data("value");
                }
                var attrs = JsViews.observable(data.dataset.meta.attributes);
                attrs.remove(index);
                // 削除ボタンのインデクスを振りなおすために、refreshしている
                attrs.refresh(data.dataset.meta.attributes);
                setAttributeTypeahead(root);
            });
            root.find("#dataset-basics-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#dataset-basics-submit"));
                root.find("#dataset-basics").find("input,textarea,select,a.btn").attr("disabled", true);
                root.find("#dataset-basics").find("input.tt-input").css("background-color", "");
                
                Service.instance.updateDatasetMetadata(id, data.dataset.meta).then(
                    function (_) {
                        binding.setProperty('dataset.errors.meta.name', "");
                        binding.setProperty('dataset.errors.meta.description', "");
                        binding.setProperty('dataset.errors.meta.license', "");
                        binding.setProperty('dataset.errors.meta.attributes', "");
                        Notification.show("success", "save successful");
                    },
                    function (e: Dynamic) {
                        switch (e.status) {
                            case 400: // BadRequest
                                switch (e.responseJSON.status) {
                                    case ApiStatus.IllegalArgument:
                                        binding.setProperty('dataset.errors.meta.name', "");
                                        binding.setProperty('dataset.errors.meta.description', "");
                                        binding.setProperty('dataset.errors.meta.license', "");
                                        binding.setProperty('dataset.errors.meta.attributes', "");
                                        var name = StringTools.replace(e.responseJSON.data.key, "d\\.", "");
                                        binding.setProperty('dataset.errors.meta.${name}', StringTools.replace(e.responseJSON.data.value, "d.", ""));
                                    case ApiStatus.BadRequest:
                                        binding.setProperty('dataset.errors.meta.license', "");
                                        binding.setProperty('dataset.errors.meta.license', StringTools.replace(e.responseJSON.data, "d.", ""));
                                }
                        }
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-basics-submit"));
                        root.find("#dataset-basics").find("input,textarea,select,a.btn").removeAttr("disabled");
                        root.find("#dataset-basics").find("input.tt-input").css("background-color", "transparent");
                    }
                );
            });
            root.find("#csv-file").on("change", function(_) {
                var element = Browser.document.getElementById("csv-file");
                var files = cast(element, InputElement).files;
                if (files.length == 0) {
                    return;
                }
                var file = files.item(0);
                var fileReader = new FileReader();
                fileReader.onload = function(evt) {
                    var fileString = evt.target.result;
                    removeAttributeTypeahead(root);
                    var attrs = JsViews.observable(data.dataset.meta.attributes);
                    new CSV(fileString).forEach(function(result) {
                        var name = result[0];
                        var value = result[1];
                        attrs.insert({ name: name, value: value });
                    });
                    setAttributeTypeahead(root);
                    root.find("#csv-file").val("");
                };
                fileReader.readAsText(file, "UTF-8");
            });
            
            // icon
            root.find("#dataset-icon-select").on("click", function (_) {
                showSelectImageDialog(id, binding).then(function(image) {
                    Service.instance.setDatasetImagePrimary(id, image.id).then(
                        function (_) {
                            binding.setProperty("dataset.primaryImage.id", image.id);
                            binding.setProperty("dataset.primaryImage.url", image.url);
                            binding.setProperty('dataset.errors.icon', "");
                            Notification.show("success", "save successful");
                        },
                        function (e: Dynamic) {
                            switch (e.status) {
                                case 400: //BadRequest
                                    switch (e.responseJSON.status) {
                                        case ApiStatus.IllegalArgument :
                                            binding.setProperty('dataset.errors.icon', "");
                                            binding.setProperty('dataset.errors.icon', StringTools.replace(e.responseJSON.data.value, "d.", ""));
                                    }
                            }
                        }
                    );
                });
            });

            // files
            // 指定したIDの表示/非表示を切り替える
            function updateAddFileButton(formId, e : Dynamic) {
                // アップロードするファイルがある場合、表示。ない場合、非表示。
                if (new JqHtml(e.target).val() != "") {
                    root.find(formId).show();
                } else {
                    root.find(formId).hide();
                }
            }
            // 指定したフォームのID(formId)が保持しているファイルをServerにアップロードする
            // submitId: Uploadボタン(aタグ)、formId: Add fileフォーム
            function uploadFiles(submitId, formId) {
                var fileButtonPath : String = formId + " input";
                BootstrapButton.setLoading(root.find(submitId));
                // ファイルをアップロードし、Datasetに追加
                Service.instance.addDatasetFiles(id, root.find(formId)).then(
                    function (res) {
                        root.find(submitId).hide();
                        root.find(fileButtonPath).val("");
                        setFileCount(data, data.dataset.files.total + res.length);
                        loadFiles(data, root, navigation);
                        Notification.show("success", "save successful");
                        JsViews.observable(data.dataset.updatedFiles).refresh(res);
                    },
                    function (e) {
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                    },
                    function () {
                        BootstrapButton.reset(root.find(submitId));
                        root.find(fileButtonPath).removeAttr("disabled");
                    }
                );
                // フォームのinputタグを無効にする
                root.find(fileButtonPath).attr("disabled", true);
            }

            // 以下のイベントハンドラの付与は、対象が常にDOM上に存在する(表示/非表示で制御)
            
            // #dataset-file-add-formの状態が変更したときに動作する
            root.find("#dataset-file-add-form").on("change", "input[type=file]", function (e) {
                // #dataset-file-add-submitの表示/非表示を切り替える
                updateAddFileButton("#dataset-file-add-submit", e);
            });
            // #dataset-file-add-submitをクリックしたときに動作する (ファイルのアップロード)
            root.find("#dataset-file-add-submit").on("click", function (_) {
                uploadFiles("#dataset-file-add-submit", "#dataset-file-add-form");
            });
            // #dataset-file-add-form-topの状態が変更したときに動作する
            root.find("#dataset-file-add-form-top").on("change", "input[type=file]", function (e) {
                // #dataset-file-add-submit-topの表示/非表示を切り替える
                updateAddFileButton("#dataset-file-add-submit-top", e);
            });
            // #dataset-file-add-submit-topをクリックしたときに動作する (ファイルのアップロード)
            root.find("#dataset-file-add-submit-top").on("click", function (_) {
                uploadFiles("#dataset-file-add-submit-top", "#dataset-file-add-form-top");
            });

            // 各ファイル要素をクリックしたときの動作を登録する
            setFileEditEvents(data, root, navigation);
            // ページャの状態が変化したときに動作する
            JsViews.observe(data.dataset.files, "index", function (_, _) {
                loadFiles(data, root, navigation);
            });
            
            // 初回読み込み
            loadFiles(data, root, navigation);

            // Access Control
            function loadOwnerships() {
                Service.instance.getOwnerships(id).then(function (x) {
                    var owners = {
                        index: Math.ceil(x.summary.offset / 20),
                        total: x.summary.total,
                        items: x.results,
                        pages: Math.ceil(x.summary.total / 20)
                    };
                    binding.setProperty("owners", Async.Completed(owners));
                    JsViews.observe(owners, "index", function (_, _) {
                        var i = owners.index;
                        Service.instance.getOwnerships(id, { offset: 20 * i, limit: 20 } ).then(function (x) {
                            var b = JsViews.observable(owners);
                            b.setProperty("index", i);
                            b.setProperty("total", x.summary.total);
                            b.setProperty("items", x.results);
                            b.setProperty("pages", Math.ceil(x.summary.total / 20));
                        });
                    });
                });
            }
            
            loadOwnerships();
            
            function getOwnershipByElement(target: EventTarget): Option<dsmoq.models.DatasetOwnership> {
                var node = JQuery._(target);
                var index = node.parents("tr[data-index]").data("index");
                return switch (data.owners) {
                    case Async.Completed(owners):
                        OptionTools.toOption(owners.items[index]);
                    case _:
                        Option.None;
                }
            }
            
            root.find("#dataset-acl").on("click", "#add-owner-item", function (_) {
                showAddOwnerDialog(data.myself).then(function (owners) {
                    ViewTools.showLoading("body");
                    Service.instance.updateDatasetACL(id, owners.map(function (x) {
                        return {
                            id: x.id,
                            ownerType: x.dataType,
                            accessLevel: DatasetPermission.LimitedRead
                        }
                    })).then(function (x) {
                        ViewTools.hideLoading("body");
                        loadOwnerships();
                        Notification.show("success", "save successful");
                    }, function (err) {
                        ViewTools.hideLoading("body");
                    });
                });
            });

            root.find("#dataset-acl").on("change", ".dsmoq-acl-select", function (e) {
                // bindingが更新タイミングの問題があるため、setImmediateを挟む
                JsTools.setImmediate(function () {
                    getOwnershipByElement(e.currentTarget).iter(function (owner) {
                        Service.instance.updateDatasetACL(id,
                            [{
                                id: owner.id,
                                ownerType: owner.ownerType,
                                accessLevel: owner.accessLevel
                            }]
                        ).then(function (_) {
                            Notification.show("success", "save successful");
                            loadOwnerships();
                        });
                    });
                });
            });
            
            root.find("#dataset-acl").on("click", ".dsmoq-remove-button", function (e) {
                getOwnershipByElement(e.currentTarget).iter(function (owner) {
                    ViewTools.showConfirm("Are you sure you want to remove?").then(function (isOk) {
                        if (isOk) {
                            ViewTools.showLoading("body");
                            Service.instance.updateDatasetACL(id, [{
                                    id: owner.id,
                                    ownerType: owner.ownerType,
                                    accessLevel :DatasetPermission.Deny
                                }]
                                ).then(function (x) {
                                    loadOwnerships();
                                    ViewTools.hideLoading("body");
                                    Notification.show("success", "remove successful");
                                }, function (err) {
                                    ViewTools.hideLoading("body");
                                }
                            );
                        }
                    });
                });
            });
            
            root.find("#dataset-guest-access-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#dataset-guest-access-submit"));
                root.find("#dataset-guest-access-form input").attr("disabled", true);
                Service.instance.setDatasetGuestAccessLevel(id, data.dataset.defaultAccessLevel).then(
                    function (_) {
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-guest-access-submit"));
                        root.find("#dataset-guest-access-form input").removeAttr("disabled");
                    }
                );
            });
            
            root.find("#dataset-storage-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#dataset-storage-submit"));
                root.find("#dataset-storage-form input").attr("disabled", true);
                Service.instance.changeDatasetStorage(id, JQuery._("#saveLocalStorage").prop("checked"), JQuery._("#saveS3Storage").prop("checked")).then(
                    function (_) {
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-storage-submit"));
                        root.find("#dataset-storage-form input").removeAttr("disabled");
                    }
                );
            });
            
            // featured
            root.find("#dataset-featured-select").on("click", function (_) {
                showSelectImageDialog(id, binding).then(function(image) {
                    Service.instance.setDatasetImageFeatured(id, image.id).then(
                        function (_) {
                            binding.setProperty("dataset.featuredImage.id", image.id);
                            binding.setProperty("dataset.featuredImage.url", image.url);
                            Notification.show("success", "save successful");
                        }
                    );
                });
            });
            
            // app
            root.find("#dataset-app-select").on("click", function (_) {
                showSelectAppDialog(id, binding).then(function(app) {
                    Service.instance.setDatasetAppPrimary(id, app.id).then(
                        function (_) {
                            if (data.dataset.primaryApp == null) {
                                binding.setProperty("dataset.primaryApp", app);
                            } else {
                                binding.setProperty("dataset.primaryApp.id", app.id);
                                binding.setProperty("dataset.primaryApp.name", app.name);
                            }
                            Notification.show("success", "save successful");
                        }
                    );
                });
            });
            Service.instance.getPrimaryDatasetApp(id).then(function(app) {
                binding.setProperty("dataset.primaryApp", app);
            });

        });

        return navigation.promise;
    }

    /**
     * オーナーを追加するダイアログを表示する。
     *
     * @param myself ログインユーザ情報
     * @return モーダルダイアログを表示するPromise
     */
    static function showAddOwnerDialog(myself: Profile) {
        var data = {
            query: "",
            offset: 0,
            hasPrev: false,
            hasNext: false,
            items: new Array<{selected: Bool, item: SuggestedOwner}>(),
            selectedIds: new Array<String>(),
            selectedItems: new Array<SuggestedOwner>()
        }
        // selectedItems -> 選択しているOwnerの情報を保持するリスト
        //   Applyボタン押下時にこのArrayを使用する
        var binding = JsViews.observable(data);
        var tpl = JsViews.template(Resource.getString("template/dataset/add_owner_dialog"));
        
        return ViewTools.showModal(tpl, data, function (html, ctx) {
            function searchOwnerCandidate(?query: String, offset = 0) {
                var limit = ImageCandicateSize + 1;
                Service.instance.findUsersAndGroups({ query: query, offset: offset, limit: limit, excludeIds: [myself.id] }).then(function (owners) {
                    var list = owners.slice(0, OwnerCandicateSize)
                                    .map(function (x) return {
                                        selected: data.selectedIds.indexOf(x.id) >= 0,
                                        item: x
                                    });
                    var hasPrev = offset > 0;
                    var hasNext = owners.length > OwnerCandicateSize;
                    binding.setProperty("offset", offset);
                    binding.setProperty("hasPrev", hasPrev);
                    binding.setProperty("hasNext", hasNext);
                    JsViews.observable(data.items).refresh(list);
                });
            }

            JsViews.observable(data.items).observeAll(function (e, args) {
                // Ownerのチェックボックスを選択/解除した時の動作
                if (args.path == "selected") {
                    var owner: SuggestedOwner = e.target.item;
                    var ids = data.selectedIds.copy();
                    // 変更用にselectedItemsを一旦コピー
                    var sItems = data.selectedItems.copy();
                    var b = JsViews.observable(data.selectedIds);
                    // 選択した場合、管理情報にデータを追加
                    if (args.value) {
                        if (ids.indexOf(owner.id) < 0) {
                            ids.push(owner.id);
                            b.refresh(ids);
                            // 選択したOwnerの情報をArrayに追加し、反映
                            sItems.push(owner);
                            JsViews.observable(data.selectedItems).refresh(sItems);
                        }
                    // 解除した場合、管理情報からデータを削除
                    } else {
                        if (ids.remove(owner.id)) {
                            b.refresh(ids);
                        }
                        // 選択を解除したOwner情報をArrayから削除し、反映
                        sItems = sItems.filter(function (x) return ids.indexOf(x.id) >= 0);
                        JsViews.observable(data.selectedItems).refresh(sItems);
                    }
                    // 選択しているOwnerの数を表示(更新)
                    html.find("#add-owner-selected-count").text(data.selectedIds.length);
                }
            });

            binding.setProperty("query", "");
            JsViews.observable(data.selectedIds).refresh([]);
            searchOwnerCandidate();

            html.find("#owner-search-form").on("submit", function (e: Event) {
                e.preventDefault();
                searchOwnerCandidate(data.query);
            });

            html.find("#owner-list-prev").on("click", function (_) {
                var query = data.query;
                var offset = data.offset - OwnerCandicateSize;
                searchOwnerCandidate(query, offset);
            });

            html.find("#owner-list-next").on("click", function (_) {
                var query = data.query;
                var offset = data.offset + OwnerCandicateSize;
                searchOwnerCandidate(query, offset);
            });

            // Applyボタンを押下したときの処理
            html.on("click", "#add-owner-dialog-submit", function (e) {
                // 選択しているOwnerの情報をServerに送信
                ctx.fulfill(data.selectedItems);
            });
        });
    }
    
    /**
     * 画像を選択するダイアログを表示する。
     *
     * @param id データセットID
     * @param rootBinding JsViewsのObservable
     * @return モーダルダイアログを表示するPromise
     */
    static function showSelectImageDialog(id: String, rootBinding: Observable): Promise<DatasetImage> {
        return showSelectDialog(
            id,
            rootBinding,
            "image",
            "template/share/select_image_dialog",
            ImageCandicateSize,
            function(datasetId, offset, limit): Promise<RangeSlice<DatasetImage>> {
                return Service.instance.getDatasetImage(datasetId, { offset: offset, limit: limit });
            },
            function(datasetId, file): Promise<{images: Array<Image>, primaryImage: String}> {
                return Service.instance.addDatasetImage(datasetId, file);
            },
            null,
            null,
            function(datasetId, imageId): Promise<{primaryImage: String, featuredImage: String}> {
                return Service.instance.removeDatasetImage(datasetId, imageId);
            },
            function(ids, data) {
                function getUrl(id: String) {
                    return data.items
                        .filter(function(x) { return x.item.id == id; })
                        .map(function(x) { return x.item.url; })[0]
                    ;
                }
                rootBinding.setProperty("dataset.primaryImage.id", ids.primaryImage);
                rootBinding.setProperty("dataset.primaryImage.url", getUrl(ids.primaryImage));
                rootBinding.setProperty("dataset.featuredImage.id", ids.featuredImage);
                rootBinding.setProperty("dataset.featuredImage.url", getUrl(ids.featuredImage));
            }
        );
    }
    /**
     * アプリを選択するダイアログを表示する。
     *
     * @param id データセットID
     * @param rootBinding JsViewsのObservable
     * @return モーダルダイアログを表示するPromise
     */
    static function showSelectAppDialog(id: String, rootBinding: Observable): Promise<DatasetApp> {
        return showSelectDialog(
            id,
            rootBinding,
            "app",
            "template/dataset/select_app_dialog",
            AppCandicateSize,
            function(datasetId, offset, limit): Promise<RangeSlice<DatasetApp>> {
                return Service.instance.getDatasetApps(datasetId, { offset: offset, limit: limit });
            },
            function(datasetId, file): Promise<DatasetApp> {
                return Service.instance.addDatasetApp(datasetId, file);
            },
            function(datasetId, appId, file): Promise<DatasetApp> {
                return Service.instance.upgradeDatasetApp(datasetId, appId, file);
            },
            null,
            function(datasetId, appId): Promise<Unit> {
                return Service.instance.removeDatasetApp(datasetId, appId);
            },
            function(_, data) {
                var primary = data.items
                    .filter(function(x) { return x.item.isPrimary; })
                    .map(function(x) { return x.item; })
                ;
                if (primary.length == 0) {
                    rootBinding.setProperty("dataset.primaryApp", null);
                } else {
                    rootBinding.setProperty("dataset.primaryApp", { id: primary[0].id, name: primary[0].name });
                }
            }
        );
    }

    /**
     * 選択ダイアログを表示する。
     *
     * @param id データセットID
     * @param rootBinding JsViewsのObservable
     * @return モーダルダイアログを表示するPromise
     */
    static function showSelectDialog<T: { id: String }, U, R>(
        datasetId: String,
        rootBinding: Observable,
        name: String,
        templatePath: String,
        candicateSize: Int,
        get: String -> Int -> Int -> Promise<RangeSlice<T>>,
        add: String -> JqHtml -> Promise<Dynamic>,
        upgrade: String -> String -> JqHtml -> Promise<U>,
        upgraded: U -> DatasetEditSelect<T> -> Void,
        remove: String -> String -> Promise<R>,
        removed: R -> DatasetEditSelect<T> -> Void
    ): Promise<T> {
        var data = {
            offset: 0,
            hasPrev: false,
            hasNext: false,
            items: new Array<{selected: Bool, item: T}>(),
            selectedIds: new Array<String>()
        }
        var binding = JsViews.observable(data);
        var tpl = JsViews.template(Resource.getString(templatePath));
        return ViewTools.showModal(tpl, data, function(html, ctx) {
            function searchCandidate(offset: Int = 0): Promise<RangeSlice<T>> {
                var limit = candicateSize + 1;
                return get(datasetId, offset, limit).then(function(res) {
                    var list = res.results.slice(0, candicateSize).map(function (x) {
                        return {
                            selected: data.selectedIds.indexOf(x.id) >= 0,
                            item: x
                        };
                    });
                    var hasPrev = offset > 0;
                    var hasNext = res.results.length > ImageCandicateSize;
                    binding.setProperty("offset", offset);
                    binding.setProperty("hasPrev", hasPrev);
                    binding.setProperty("hasNext", hasNext);
                    JsViews.observable(data.items).refresh(list);
                });
            }
            function filterSelected() {
                return data.items
                    .filter(function (x) return x.selected)
                    .map(function (x) return x.item)
                ;
            }
            function execute<T>(type: String, exec: Void -> Promise<T>, after: Null<T -> Void> = null) {
                var isPrevEnabled = html.find('#${name}-list-prev').attr("disabled") != "disabled";
                var isNextEnabled = html.find('#${name}-list-next').attr("disabled") != "disabled";
                var selectedIds = data.selectedIds.concat([]);
                // Apply,Delete,UpgradeをDisableにする
                JsViews.observable(data.selectedIds).refresh([]);
                html.find('#${name}-list-prev').attr("disabled", "disabled");
                html.find('#${name}-list-next').attr("disabled", "disabled");
                html.find('#select-${name}-dialog-cancel').attr("disabled", "disabled");
                html.find('#upload-${name}').attr("disabled", "disabled");
                // 直接Loadingを指定すると、内部のinput要素までloading-textで置き換わるため、模倣している。
                // メッセージは内部のdivに担当させ、disableのみを#type-nameボタンに設定する
                BootstrapButton.setLoading(html.find('#${type}-${name} > div'));
                exec().then(
                    function (x) {
                        Notification.show("success", "save successful");
                        var p = searchCandidate();
                        if (after != null) {
                            p.then(function(_) {
                                after(x);
                            });
                        }
                    },
                    function (e) {
                        // 失敗時には選択設定を戻す
                        JsViews.observable(data.selectedIds).refresh(selectedIds);
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                    },
                    function () {
                        html.find('#${type}-${name}-form input').val(""); // TODO IE11で要検証
                        BootstrapButton.reset(html.find('#${type}-${name} > div'));
                        html.find('#upload-${name}').removeAttr("disabled");
                        if (isPrevEnabled) {
                            html.find('#${name}-list-prev').removeAttr("disabled");
                        }
                        if (isNextEnabled) {
                            html.find('#${name}-list-next').removeAttr("disabled");
                        }
                        html.find('#select-${name}-dialog-cancel').removeAttr("disabled");
                    }
                );
            }
            JsViews.observable(data.items).observeAll(function (e, args) {
                if (args.path == "selected") {
                    var item: T = e.target.item;
                    var ids = data.selectedIds.copy();
                    var b = JsViews.observable(data.selectedIds);
                    if (args.value) {
                        if (ids.indexOf(item.id) < 0) {
                            ids.push(item.id);
                            b.refresh(ids);
                        }
                    } else {
                        if (ids.remove(item.id)) {
                            b.refresh(ids);
                        }
                    }
                }
            });
            var binding = JsViews.observable(data.selectedIds).refresh([]);
            searchCandidate();
            html.find('#upload-${name}-form input').on("change", function(_) {
                execute(
                    "upload",
                    function() {
                        return add(datasetId, html.find('#upload-${name}-form'));
                    }
                );
            });
            html.find('#upgrade-${name}-form input').on("change", function(_) {
                execute(
                    "upgrade",
                    function() {
                        var selected = html.find("input:checked").val();
                        return upgrade(datasetId, selected, html.find('#upgrade-${name}-form'));
                    },
                    function(x) {
                        upgraded(x, data);
                    }
                );
            });
            html.find('#delete-${name}').on("click", function(_) {
                execute(
                    "delete",
                    function() {
                        var selected = html.find("input:checked").val();
                        return remove(datasetId, selected);
                    },
                    function(x) {
                        removed(x, data);
                    }
                );
            });
            html.find('#${name}-list-prev').on("click", function (_) {
                var offset = data.offset - candicateSize;
                searchCandidate(offset);
            });
            html.find('#${name}-list-next').on("click", function (_) {
                var offset = data.offset + candicateSize;
                searchCandidate(offset);
            });
            html.on("click", '#select-${name}-dialog-submit', function (e) {
                ctx.fulfill(filterSelected()[0]);
            });
        });
    }

    /**
     * 指定したページのファイル一覧を読み込みます。
     * 
     * @param data データバインドされた画面データ
     * @param root
     * @param navigation
     */
    static function loadFiles(data: DatasetEdit, root: Html, navigation: PromiseBroker<Navigation<Page>>) {
        if (data == null) {
            trace("DatasetEditPage.loadFiles: invalid data - null");
            return;
        }
        if (root == null) {
            trace("DatasetEditPage.loadFiles: invalid root - null");
            return;
        }
        if (navigation == null) {
            trace("DatasetEditPage.loadFiles: invalid navigation - null");
            return;
        }
        JsViews.observable(data.dataset.updatedFiles).refresh([]);
        var newIndex = Std.int(Math.max(0, Math.min(data.dataset.files.index, data.dataset.files.pages - 1)));
        JsViews.observable(data.dataset.files).setProperty({ index: newIndex, useProgress: true });
        var offset = newIndex * data.dataset.files.limit;
        Service.instance.getDatasetFiles(data.dataset.id, { limit: data.dataset.files.limit, offset: offset }).then(function (res) {
            JsViews.observable(data.dataset.files.items).refresh(res.results);
            setFileCount(data, res.summary.total);
            JsViews.observable(data.dataset.files).setProperty("useProgress", false);
        }, function (err: Dynamic) {
            JsViews.observable(data.dataset.files).setProperty("useProgress", false);
            switch (err.status) {
                case 400: // BadRequest
                    switch (err.responseJSON.status) {
                        case ApiStatus.BadRequest: navigation.fulfill(Navigation.Navigate(Page.Top));
                        case ApiStatus.IllegalArgument: navigation.fulfill(Navigation.Navigate(Page.Top));
                    }
                case 403: // Forbidden
                    switch (err.responseJSON.status) {
                        case ApiStatus.Unauthorized: navigation.fulfill(Navigation.Navigate(Page.Top));
                        case ApiStatus.AccessDenied: navigation.fulfill(Navigation.Navigate(Page.Top));
                    }
                case 404: // NotFound
                    switch (err.responseJSON.status) {
                        case ApiStatus.NotFound: navigation.fulfill(Navigation.Navigate(Page.Top));
                    }
                case _: // その他(500系など)
                    root.html("Network error");
            }
        });
    }
    /**
     * ファイル総数を設定します。
     * 
     * @param data データバインドされた画面データ
     * @param count 設定するファイル総数
     */
    static function setFileCount(data: DatasetEdit, count: Int) {
        if (data == null) {
            trace("DatasetEditPage.setFileCount: invalid data - null");
            return;
        }
        if (count < 0) {
            trace('DatasetEditPage.setFileCount: invalid count - ${count}');
            return;
        }
        var pages = calcPages(count, data.dataset.files.limit);
        JsViews.observable(data.dataset.files).setProperty({ total: count, pages: pages });
    }
    /**
     * ページ数を計算します。
     * 
     * @param total ファイル総数
     * @param limit 1ページあたりのファイル数
     * @return ページ数
     */
    static function calcPages(total: Int, limit: Int): Int {
        if (limit <= 0) {
            trace('DatasetEditPage.calcPages: invalid limit - ${limit}');
            return 0;
        }
        return Math.ceil(total / limit);
    }
    /**
     * ファイル一覧部にイベントハンドラを付与します。
     * 
     * @param data データバインドされた画面データ
     * @param root
     * @param navigation
     */
    static function setFileEditEvents(data: DatasetEdit, root: Html, navigation: PromiseBroker<Navigation<Page>>) {
        if (data == null) {
            trace("DatasetEditPage.setFileEditEvents: invalid data - null");
            return;
        }
        if (root == null) {
            trace("DatasetEditPage.setFileEditEvents: invalid root - null");
            return;
        }
        if (navigation == null) {
            trace("DatasetEditPage.setFileEditEvents: invalid navigation - null");
            return;
        }
        var id = data.dataset.id;
        root.find(".dataset-file-edit-start").off();
        root.find(".dataset-file-edit-submit").off();
        root.find(".dataset-file-edit-cancel").off();
        root.find(".dataset-file-replace-start").off();
        root.find(".dataset-file-replace-submit").off();
        root.find(".dataset-file-replace-cancel").off();
        root.find(".dataset-file-delete").off();
        root.on("click", ".dataset-file-edit-start", function (e) {
            var fid: String = new JqHtml(e.target).data("value");
            var file = data.dataset.files.items.filter(function (x) return x.id == fid)[0];
            var edit = {
                name: file.name,
                description: file.description,
                errors: { name: "" }
            };
            var binding = JsViews.observable(edit);

            var target = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-edit");
            var menu = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-menu");
            var btns = new JqHtml(e.target).parents(".dataset-file").find(".btn");

            var tpl = JsViews.template(Resource.getString("share/dataset/file/edit"));
            tpl.link(target, edit);
            menu.hide();

            function close() {
                target.empty();
                menu.show();
                tpl.unlink(target);
                target.off();
            }

            target.on("click", ".dataset-file-edit-submit", function (_) {
                btns.attr("disabled", true);
                Service.instance.updateDatatetFileMetadata(id, fid, edit.name, edit.description).then(
                    function (res) {
                        close();
                        Notification.show("success", "save successful");
                        loadFiles(data, root, navigation);
                        JsViews.observable(data.dataset.updatedFiles).refresh([res]);
                    },
                    function (e) {
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                    },
                    function () {
                        btns.removeAttr("disabled");
                    }
                );
            });

            target.on("click", ".dataset-file-edit-cancel", function (_) {
                close();
            });
        });
        root.on("click", ".dataset-file-replace-start", function (e) {
            var fid: String = new JqHtml(e.target).data("value");
            var file = data.dataset.files.items.filter(function (x) return x.id == fid)[0];

            var target = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-replace");
            var menu = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-menu");
            var btns = new JqHtml(e.target).parents(".dataset-file").find(".btn");

            target.html(Resource.getString("share/dataset/file/replace"));
            menu.hide();

            function close() {
                target.empty();
                menu.show();
                target.off();
            }

            target.find("input[type=file]").on("change", function (e) {
                if (new JqHtml(e.target).val() != "") {
                    target.find(".dataset-file-replace-submit").attr("disabled", false);
                } else {
                    target.find(".dataset-file-replace-submit").attr("disabled", true);
                }
            });

            target.on("click", ".dataset-file-replace-submit", function (_) {
                btns.attr("disabled", true);
                Service.instance.replaceDatasetFile(id, fid, target.find("form")).then(
                    function (res) {
                        close();
                        Notification.show("success", "save successful");
                        loadFiles(data, root, navigation);
                        JsViews.observable(data.dataset.updatedFiles).refresh([res]);
                    },
                    function (e) {
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                    },
                    function () {
                        btns.removeAttr("disabled");
                    }
                );
            });

            target.on("click", ".dataset-file-replace-cancel", function (_) {
                close();
            });
        });
        root.on("click", ".dataset-file-delete", function (e) {
            var fid: String = new JqHtml(e.target).data("value");
            var btns = new JqHtml(e.target).parents(".dataset-file").find(".btn");
            btns.attr("disabled", true);
            JsTools.confirm("Are you sure you want to delete this file?").flatMap(function (_) {
                return Service.instance.removeDatasetFile(id, fid);
            }).then(
                function (_) {
                    Notification.show("success", "delete successful");
                    setFileCount(data, data.dataset.files.total - 1);
                    loadFiles(data, root, navigation);
                },
                function (e) {
                    // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                    // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                },
                function () {
                    btns.removeAttr("disabled");
                }
            );
        });
    }
}
