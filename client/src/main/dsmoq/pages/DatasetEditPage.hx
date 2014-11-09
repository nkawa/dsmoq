package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.models.DatasetPermission;
import dsmoq.models.Service;
import dsmoq.views.AutoComplete;
import haxe.Resource;
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

class DatasetEditPage {
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
                    meta: x.meta,
                    files: x.files,
                    ownerships: x.ownerships,
                    defaultAccessLevel: x.defaultAccessLevel,
                    primaryImage: x.primaryImage,
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
                }
            };
            rootBinding.setProperty("data", data);

            var binding = JsViews.observable(rootBinding.data().data);

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
                var attrs = JsViews.observable(data.dataset.meta.attributes);
                attrs.remove(index);
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

            // icon
            root.find("#dataset-icon-form").on("change", "input[type=file]", function (e) {
                if (new JqHtml(e.target).val() != "") {
                    root.find("#dataset-icon-submit").show();
                } else {
                    root.find("#dataset-icon-submit").hide();
                }
            });
            root.find("#dataset-icon-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#dataset-icon-submit"));
                Service.instance.changeDatasetImage(id, JQuery._("#dataset-icon-form")).then(
                    function (res) {
                        var img = res.images.filter(function (x) return x.id == res.primaryImage)[0];
                        binding.setProperty("dataset.primaryImage.id", img.id);
                        binding.setProperty("dataset.primaryImage.url", img.url);
                        binding.setProperty('dataset.errors.icon', "");
                        root.find("#dataset-icon-form input[type=file]").val("");
                        root.find("#dataset-icon-submit").hide();
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
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-icon-submit"));
                        root.find("#dataset-icon-form input").removeAttr("disabled");
                    }
                );
                root.find("#dataset-icon-form input").attr("disabled", true);
            });

            // files
            root.find("#dataset-file-add-form").on("change", "input[type=file]", function (e) {
                if (new JqHtml(e.target).val() != "") {
                    root.find("#dataset-file-add-submit").show();
                } else {
                    root.find("#dataset-file-add-submit").hide();
                }
            });
            root.find("#dataset-file-add-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#dataset-file-add-submit"));
                Service.instance.addDatasetFiles(id, root.find("#dataset-file-add-form")).then(
                    function (res) {
                        root.find("#dataset-file-add-submit").hide();
                        JsViews.observable(data.dataset.files).insert(res[0]);
                        root.find("#dataset-file-add-form input").val("");
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        Notification.show("error", "error happened");
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-file-add-submit"));
                        root.find("#dataset-file-add-form input").removeAttr("disabled");
                    }
                );
                root.find("#dataset-file-add-form input").attr("disabled", true);
            });

            root.on("click", ".dataset-file-edit-start", function (e) {
                var fid: String = new JqHtml(e.target).data("value");
                var file = data.dataset.files.filter(function (x) return x.id == fid)[0];
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
                var file = data.dataset.files.filter(function (x) return x.id == fid)[0];

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
                            Notification.show("error", "error happened");
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
                        var files = data.dataset.files.filter(function (x) return x.id != fid);
                        JsViews.observable(data.dataset.files).refresh(files);
                        Notification.show("success", "delete successful");
                    },
                    function (e) {
                        Notification.show("error", "error happened");
                    },
                    function () {
                        btns.removeAttr("disabled");
                    }
                );
            });

            // Access Control
            root.find("#dataset-owner-add").on("click", function (_) {
                var owner = AutoComplete.getCompletedItem(root.find("#dataset-owner-typeahead"));
                var ownerships = JsViews.observable(data.dataset.ownerships);
                ownerships.insert({
                    id: owner.id,
                    name: owner.name,
                    fullname: owner.fullname,
                    organization: owner.organization,
                    image: owner.image,
                    ownerType: owner.dataType,
                    accessLevel: 1,
                });
            });
            root.find("#dataset-ownership-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#dataset-ownership-submit"));
                root.find("#dataset-owner-list").find("input,select,.btn").attr("disabled", true);
                root.find("#dataset-owner-list input.tt-input").css("background-color", "");
                Service.instance.updateDatasetACL(id, data.dataset.ownerships.map(function (x) {
                    return {
                        id: x.id,
                        ownerType: x.ownerType,
                        accessLevel: x.accessLevel
                    }
                })).then(
                    function (_) {
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        Notification.show("error", "error happened");
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-ownership-submit"));
                        root.find("#dataset-owner-list").find("input,select,.btn").removeAttr("disabled");
                        root.find("#dataset-owner-list input.tt-input").css("background-color", "transparent");
                    }
                );
            });
            root.find("#dataset-guest-access-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#dataset-guest-access-submit"));
                root.find("#dataset-guest-access-form input").attr("disabled", true);
                Service.instance.setDatasetGuestAccessLevel(id, data.dataset.defaultAccessLevel).then(
                    function (_) {
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        Notification.show("error", "error happened");
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-guest-access-submit"));
                        root.find("#dataset-guest-access-form input").removeAttr("disabled");
                    }
                );
            });
        });

        return navigation.promise;
    }
}