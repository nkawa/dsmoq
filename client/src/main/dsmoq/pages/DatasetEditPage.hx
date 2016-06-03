package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.models.DatasetFile;
import dsmoq.models.DatasetImage;
import dsmoq.models.DatasetPermission;
import dsmoq.models.Profile;
import dsmoq.models.Service;
import dsmoq.views.AutoComplete;
import dsmoq.models.DatasetOwnership;
import dsmoq.models.GroupRole;
import dsmoq.models.SuggestedOwner;
import dsmoq.View;
import dsmoq.views.ViewTools;
import haxe.ds.Option;
import haxe.Resource;
import hxgnd.ArrayTools;
import hxgnd.js.Html;
import hxgnd.js.JqHtml;
import hxgnd.js.JQuery;
import hxgnd.js.JsTools;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Unit;
import js.bootstrap.BootstrapButton;
import js.html.Element;
import js.html.Event;
import dsmoq.views.AutoComplete;
import hxgnd.Result;
import hxgnd.Error;
import js.Browser;
import js.html.FileReader;
import js.html.InputElement;
import dsmoq.CSV;
import js.html.EventTarget;
import js.html.OptionElement;
import js.Lib;
import dsmoq.CKEditor;

using hxgnd.OptionTools;

class DatasetEditPage {
    inline static var OwnerCandicateSize = 5;
    inline static var ImageCandicateSize = 10;

    public static function render(root: Html, onClose: Promise<Unit>, id: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        function setAttributeTypeahead(root: JqHtml) {
            AutoComplete.initialize(root.find(".attribute-typeahead"), {
                url: function (query: String) {
                    return '/api/suggests/attributes?query=${query}';
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
        .thenError(function (err) {
            root.html(switch (err.name) {
                case ServiceErrorType.NotFound: "Not found";
                case ServiceErrorType.Unauthorized: "Permission denied";
                default: "Network error";
            });
        });

        promise.then(function (x) {
            var data = {
                myself: Service.instance.profile,
                licenses: Service.instance.licenses,
                dataset: {
                    id: x.id,
                    meta: x.meta,
                    files: new Array<{ isAppend: Bool, file: DatasetFile }>(),
                    ownerships: x.ownerships,
                    defaultAccessLevel: x.defaultAccessLevel,
                    primaryImage: x.primaryImage,
                    featuredImage: x.featuredImage,
                    localState: x.localState,
                    s3State: x.s3State,
                    filesCount: x.filesCount,
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
                owners: Async.Pending,
                fileLimit: x.fileLimit
            };
            var fileOffset = 0;
            var isZeroFileFirst = x.filesCount == 0;
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
                    return '/api/suggests/users_and_groups?query=${query}';
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
                    function (e) {
                        switch (e.name) {
                            case ServiceErrorType.BadRequest:
                                binding.setProperty('dataset.errors.meta.name', "");
                                binding.setProperty('dataset.errors.meta.description', "");
                                binding.setProperty('dataset.errors.meta.license', "");
                                binding.setProperty('dataset.errors.meta.attributes', "");
                                for (x in cast(e, ServiceError).detail) {
                                    binding.setProperty('dataset.errors.meta.${x.name}', x.message);
                                }
                        }
                        Notification.show("error", "error happened");
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
                        function (e) {
                            switch (e.name) {
                                case ServiceErrorType.BadRequest:
                                    binding.setProperty('dataset.errors.icon', "");
                                    for (x in cast(e, ServiceError).detail) {
                                        if (x.name == "file") binding.setProperty('dataset.errors.icon', x.message);
                                    }
                            }
                            Notification.show("error", "error happened");
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
            function setFileEditEvents() {
                root.find(".more").off();
                root.find(".dataset-file-edit-start").off();
                root.find(".dataset-file-edit-submit").off();
                root.find(".dataset-file-edit-cancel").off();
                root.find(".dataset-file-replace-start").off();
                root.find(".dataset-file-replace-submit").off();
                root.find(".dataset-file-replace-cancel").off();
                root.find(".dataset-file-delete").off();
                root.find(".more").on("click", function (e) {
                    Service.instance.getDatasetFiles(id, { limit: data.fileLimit, offset: fileOffset }).then(function (res) {
                        for (file in res.results) {
                            if (data.dataset.files.map(function(x) { return x.file.id; }).indexOf(file.id) == -1) {
                                JsViews.observable(data.dataset.files).insert({ isAppend: false, file: file });
                            }
                        }
                        fileOffset += res.summary.count;
                        setFileEditEvents();
                    }, function (err) {
                        switch (err.name) {
                            case ServiceErrorType.Unauthorized:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                            case ServiceErrorType.NotFound:
                                navigation.fulfill(Navigation.Navigate(Page.Top));
                            default:
                                trace(err);
                                root.html("Network error");
                        }
                    });
                });
                root.on("click", ".dataset-file-edit-start", function (e) {
                    var fid: String = new JqHtml(e.target).data("value");
                    var file = data.dataset.files.filter(function (x) return x.file.id == fid)[0].file;
                    var data = {
                        name: file.name,
                        description: file.description,
                        errors: { name: "" }
                    };
                    var binding = JsViews.observable(data);

                    var target = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-edit");
                    var menu = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-menu");
                    var btns = new JqHtml(e.target).parents(".dataset-file").find(".btn");

                    var tpl = JsViews.template(Resource.getString("share/dataset/file/edit"));
                    tpl.link(target, data);
                    menu.hide();

                    function close() {
                        target.empty();
                        menu.show();
                        tpl.unlink(target);
                        target.off();
                    }

                    target.on("click", ".dataset-file-edit-submit", function (_) {
                        btns.attr("disabled", true);
                        Service.instance.updateDatatetFileMetadata(id, fid, data.name, data.description).then(
                            function (res) {
                                var fb = JsViews.observable(file);
                                fb.setProperty("name", res.name);
                                fb.setProperty("description", res.description);
                                fb.setProperty("url", res.url);
                                fb.setProperty("size", res.size);
                                fb.setProperty("createdAt", res.createdAt);
                                fb.setProperty("createdBy", res.createdBy);
                                fb.setProperty("updatedAt", res.updatedAt);
                                fb.setProperty("updatedBy", res.updatedBy);
                                close();
                                Notification.show("success", "save successful");
                            },
                            function (e) {
                                switch (e.name) {
                                    case ServiceErrorType.BadRequest:
                                        for (x in cast(e, ServiceError).detail) {
                                            binding.setProperty('errors.${x.name}', x.message);
                                        }
                                }
                                Notification.show("error", "error happened");
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
                    var file = data.dataset.files.filter(function (x) return x.file.id == fid)[0];

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
                                var fb = JsViews.observable(file);
                                fb.setProperty("name", res.name);
                                fb.setProperty("description", res.description);
                                fb.setProperty("url", res.url);
                                fb.setProperty("size", res.size);
                                fb.setProperty("createdAt", res.createdAt);
                                fb.setProperty("createdBy", res.createdBy);
                                fb.setProperty("updatedAt", res.updatedAt);
                                fb.setProperty("updatedBy", res.updatedBy);
                                close();
                                Notification.show("success", "save successful");
                            },
                            function (e) {
                                switch (e.name) {
                                    case ServiceErrorType.BadRequest:
                                        Notification.show("error", "file is empty");
                                    case ServiceErrorType.NotFound:
                                        Notification.show("error", "dataset not found");
                                    case ServiceErrorType.Unauthorized: 
                                        Notification.show("error", "permission denied");
                                    default:
                                        Notification.show("error", "error happened");
                                }
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

                root.find(".dataset-file-delete").on("click", function (e) {
                    var fid: String = new JqHtml(e.target).data("value");
                    var btns = new JqHtml(e.target).parents(".dataset-file").find(".btn");
                    btns.attr("disabled", true);
                    JsTools.confirm("Are you sure you want to delete this file?").flatMap(function (_) {
                        return Service.instance.removeDatasetFile(id, fid);
                    }).then(
                        function (_) {
                            JsViews.observable(data.dataset).setProperty("filesCount", data.dataset.filesCount - 1);
                            var target = data.dataset.files.filter(function (x) return x.file.id == fid)[0];
                            if (!target.isAppend) {
                                fileOffset--;
                            }
                            var files = data.dataset.files.filter(function (x) return x.file.id != fid);
                            JsViews.observable(data.dataset.files).refresh(files);
                            setFileEditEvents();
                            Notification.show("success", "delete successful");
                        },
                        function (e) {
                            if (e.message != "Canceled") {
                                switch (e.name) {
                                    case ServiceErrorType.NotFound:
                                        Notification.show("error", "dataset not found");
                                    case ServiceErrorType.Unauthorized: 
                                        Notification.show("error", "permission denied");
                                    default:
                                        Notification.show("error", "error happened");
                                }
                            }
                        },
                        function () {
                            btns.removeAttr("disabled");
                        }
                    );
                });
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
                        for (i in 0...res.length) {
                            JsViews.observable(data.dataset.files).insert({ isAppend: true, file: res[i] });
                        }
                        root.find(fileButtonPath).val("");

                        JsViews.observable(data.dataset).setProperty("filesCount", data.dataset.filesCount + res.length);
                        setFileEditEvents();
                        if (isZeroFileFirst) { 
                            // #dataset-file-add-formの状態が変更したときに動作する
                            root.find("#dataset-file-add-form").on("change", "input[type=file]", function (e) {
                                // #dataset-file-add-submitの表示/非表示を切り替える
                                updateAddFileButton("#dataset-file-add-submit", e);
                            });
                            // #dataset-file-add-submitをクリックしたときに動作する (ファイルのアップロード)
                            root.find("#dataset-file-add-submit").on("click", function (_) {
                                uploadFiles("#dataset-file-add-submit", "#dataset-file-add-form");
                            });
                            isZeroFileFirst = false;
                        }
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        switch (e.name) {
                            case ServiceErrorType.BadRequest:
                                Notification.show("error", "file is empty");
                            case ServiceErrorType.NotFound:
                                Notification.show("error", "dataset not found");
                            case ServiceErrorType.Unauthorized: 
                                Notification.show("error", "permission denied");
                            default:
                                Notification.show("error", "error happened");
                        }
                    },
                    function () {
                        BootstrapButton.reset(root.find(submitId));
                        root.find(fileButtonPath).removeAttr("disabled");
                    }
                );
                // フォームのinputタグを無効にする
                root.find(fileButtonPath).attr("disabled", true);
            }
            
            Service.instance.getDatasetFiles(id, { limit: data.fileLimit, offset: 0 }).then(function (res) {
                for (file in res.results) {
                    JsViews.observable(data.dataset.files).insert({ isAppend: false, file: file });
                }
                fileOffset += res.summary.count;
                setFileEditEvents();
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

            }, function (err) {
                switch (err.name) {
                    case ServiceErrorType.Unauthorized:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    case ServiceErrorType.NotFound:
                        navigation.fulfill(Navigation.Navigate(Page.Top));
                    default:
                        trace(err);
                        root.html("Network error");
                }
            });

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
                        }, function (e) {
                            Notification.show("error", "error happened");
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
                        switch (err.name) {
                            case ServiceErrorType.NotFound:
                                Notification.show("error", "dataset not found");
                            case ServiceErrorType.Unauthorized: 
                                Notification.show("error", "permission denied");
                            default:
                                Notification.show("error", "error happened");
                        }
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
                        }, function (e) {
                            switch (e.name) {
                                case ServiceErrorType.NotFound:
                                    Notification.show("error", "dataset not found");
                                case ServiceErrorType.Unauthorized: 
                                    Notification.show("error", "permission denied");
                                default:
                                    Notification.show("error", "error happened");
                            }
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
                                    switch (err.name) {
                                        case ServiceErrorType.NotFound:
                                            Notification.show("error", "dataset not found");
                                        case ServiceErrorType.Unauthorized: 
                                            Notification.show("error", "permission denied");
                                        default:
                                            Notification.show("error", "error happened");
                                    }
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
                        switch (e.name) {
                            case ServiceErrorType.NotFound:
                                Notification.show("error", "dataset not found");
                            case ServiceErrorType.Unauthorized: 
                                Notification.show("error", "permission denied");
                            default:
                                Notification.show("error", "error happened");
                        }
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
                        switch (e.name) {
                            case ServiceErrorType.BadRequest:
                                Notification.show("error", "select check box");
                            case ServiceErrorType.NotFound:
                                Notification.show("error", "dataset not found");
                            case ServiceErrorType.Unauthorized: 
                                Notification.show("error", "permission denied");
                            default:
                                Notification.show("error", "error happened");
                        }
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
                        },
                        function (e) {
                            Notification.show("error", "error happened");
                        }
                    );
                });                
            });
        });
        
        return navigation.promise;
    }

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

            // Apply」ボタンを押下したときの処理
            html.on("click", "#add-owner-dialog-submit", function (e) {
                // 選択しているOwnerの情報をServerに送信
                ctx.fulfill(data.selectedItems);
            });
        });
    }
    
    static function showSelectImageDialog(id: String, rootBinding: Observable) {
        var data = {
            offset: 0,
            hasPrev: false,
            hasNext: false,
            items: new Array<{selected: Bool, item: DatasetImage}>(),
            selectedIds: new Array<String>()
        }
        var binding = JsViews.observable(data);
        var tpl = JsViews.template(Resource.getString("template/share/select_image_dialog"));
        
        return ViewTools.showModal(tpl, data, function (html, ctx) {
            function searchImageCandidate(offset = 0) {
                var limit = ImageCandicateSize + 1;
                Service.instance.getDatasetImage(id, { offset: offset, limit: limit }).then(function (images) {
                    var list = images.results.slice(0, ImageCandicateSize)
                                    .map(function (x) return {
                                        selected: data.selectedIds.indexOf(x.id) >= 0,
                                        item: x
                                    });
                    var hasPrev = offset > 0;
                    var hasNext = images.results.length > ImageCandicateSize;
                    binding.setProperty("offset", offset);
                    binding.setProperty("hasPrev", hasPrev);
                    binding.setProperty("hasNext", hasNext);
                    JsViews.observable(data.items).refresh(list);
                });
            }
            
            function filterSelectedOwner() {
                return data.items
                            .filter(function (x) return x.selected)
                            .map(function (x) return x.item);
            }
            
            function getUrl(id: String) {
                return data.items.filter(function(x) {
                    return x.item.id == id;
                }).map(function(x) return x.item.url)[0];
            }

            JsViews.observable(data.items).observeAll(function (e, args) {
                if (args.path == "selected") {
                    var image: DatasetImage = e.target.item;
                    var ids = data.selectedIds.copy();
                    var b = JsViews.observable(data.selectedIds);
                    if (args.value) {
                        if (ids.indexOf(image.id) < 0) {
                            ids.push(image.id);
                            b.refresh(ids);
                        }
                    } else {
                        if (ids.remove(image.id)) {
                            b.refresh(ids);
                        }
                    }
                }
            });

            var binding = JsViews.observable(data.selectedIds).refresh([]);
            searchImageCandidate();
            
            html.find("#image-form input").on("change", function(_) {

                var isPrevEnabled = html.find("#image-list-prev").attr("disabled") != "disabled";
                var isNextEnabled = html.find("#image-list-next").attr("disabled") != "disabled";
                
                // ApplyとDeleteをDisableにするために必要
                var b = JsViews.observable(data.selectedIds);
                b.refresh([]);
                html.find("#image-list-prev").attr("disabled", "disabled");
                html.find("#image-list-next").attr("disabled", "disabled");
                html.find("#select-image-dialog-cancel").attr("disabled", "disabled");
                html.find("#upload-image").attr("disabled", "disabled");
                // 直接Loadingを指定すると、内部のinput要素までloading-textで置き換わるため、模倣している。
                // メッセージは内部のdivに担当させ、disableのみを#upload-imageボタンに設定する
                BootstrapButton.setLoading(html.find("#upload-image > div"));
                Service.instance.addDatasetImage(id, html.find("#image-form")).then(
                    function (_) {
                        Notification.show("success", "save successful");
                        searchImageCandidate();
                        // TODO IE11で要検証
                        html.find("#image-form input").val("");

                    },
                    function (e) {
                        switch (e.name) {
                            case ServiceErrorType.NotFound:
                                Notification.show("error", "not found");
                            case ServiceErrorType.Unauthorized: 
                                Notification.show("error", "permission denied");
                            default:
                                Notification.show("error", "error happened");
                        }
                    },
                    function () {
                        BootstrapButton.reset(html.find("#upload-image > div"));
                        html.find("#upload-image").removeAttr("disabled");

                        if (isPrevEnabled) {
                            html.find("#image-list-prev").removeAttr("disabled");
                        }
                        if (isNextEnabled) {
                            html.find("#image-list-next").removeAttr("disabled");
                        }
                        html.find("#select-image-dialog-cancel").removeAttr("disabled");
                    });
            });
            
            html.find("#delete-image").on("click", function(_) {
                var isPrevEnabled = html.find("#image-list-prev").attr("disabled") != "disabled";
                var isNextEnabled = html.find("#image-list-next").attr("disabled") != "disabled";

                html.find("#upload-image").attr("disabled", "disabled");
                html.find("#image-list-prev").attr("disabled", "disabled");
                html.find("#image-list-next").attr("disabled", "disabled");
                html.find("#select-image-dialog-cancel").attr("disabled", "disabled");
                var selected = html.find("input:checked").val();
                // 直接Loadingを指定すると、完了後に#delete-imageがアクティブになってしまうため、模倣している。
                // メッセージは内部のdivに担当させ、disableのみを#delete-imageボタンに設定する
                html.find("#delete-image").attr("disabled", "disabled");
                BootstrapButton.setLoading(html.find("#delete-image > div"));
                // ApplyをDisableにするために必要
                var b = JsViews.observable(data.selectedIds);
                b.refresh([]);
                Service.instance.removeDatasetImage(id, selected).then(
                    function (ids) {
                        Notification.show("success", "delete successful");
                        searchImageCandidate();
                        binding.refresh([]);
                        rootBinding.setProperty("dataset.primaryImage.id", ids.primaryImage);
                        rootBinding.setProperty("dataset.primaryImage.url", getUrl(ids.primaryImage));
                        rootBinding.setProperty("dataset.featuredImage.id", ids.featuredImage);
                        rootBinding.setProperty("dataset.featuredImage.url", getUrl(ids.featuredImage));
                    },
                    function (e) {
                        switch (e.name) {
                            case ServiceErrorType.BadRequest:
                                for (x in cast(e, ServiceError).detail) {
                                    Notification.show("error", x.message);
                                }
                            case ServiceErrorType.NotFound:
                                Notification.show("error", "not found");
                            case ServiceErrorType.Unauthorized: 
                                Notification.show("error", "permission denied");
                            default:
                                Notification.show("error", "error happened");
                        }
                    },
                    function() {
                        BootstrapButton.reset(html.find("#delete-image > div"));
                        html.find("#upload-image").removeAttr("disabled");
                        if (isPrevEnabled) {
                            html.find("#image-list-prev").removeAttr("disabled");
                        }
                        if (isNextEnabled) {
                            html.find("#image-list-next").removeAttr("disabled");
                        }
                        html.find("#select-image-dialog-cancel").removeAttr("disabled");
                    });
            } );
            
            html.find("#image-list-prev").on("click", function (_) {
                var offset = data.offset - ImageCandicateSize;
                searchImageCandidate(offset);
            });

            html.find("#image-list-next").on("click", function (_) {
                var offset = data.offset + ImageCandicateSize;
                searchImageCandidate(offset);
            });

            html.on("click", "#select-image-dialog-submit", function (e) {
                ctx.fulfill(filterSelectedOwner()[0]);
            });
        });
    }
}
