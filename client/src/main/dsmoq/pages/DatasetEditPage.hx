package dsmoq.pages;

import js.bootstrap.BootstrapButton;
import js.support.ControllableStream;
import js.jsviews.JsViews;
import js.typeahead.Bloodhound;
import js.typeahead.Typeahead;
import dsmoq.models.Service;
import js.html.Element;
import dsmoq.framework.View;
import js.jqhx.JqHtml;
import js.html.Event;
import js.jqhx.JQuery;
import js.support.JsTools;
import haxe.Resource;
import dsmoq.framework.types.PageNavigation;

class DatasetEditPage {
    public static function create(id: String) {
        var navigation = new ControllableStream();
        var attrbuteEngine = createAttributeBloodhound();
        var ownerEngine = createOwnerBloodhound();

        function setAttributeTypeahead(root: JqHtml) {
            Typeahead.initialize(root.find(".attribute-typeahead"), {
                source: attrbuteEngine.ttAdapter(),
            });
            function trigger(e: Event) { new JqHtml(e.target).trigger("change"); }
            root.find(".attribute-typeahead").on("typeahead:autocompleted", trigger);
            root.find(".attribute-typeahead").on("typeahead:selected", trigger);
        }

        function removeAttributeTypeahead(root: JqHtml) {
            Typeahead.destroy(root.find(".attribute-typeahead"));
        }

        function setOwnerTypeahead(root: JqHtml) {
            Typeahead.initialize(root.find("#dataset-owner-typeahead"), {}, {
                source: ownerEngine.ttAdapter(),
                displayKey: "name",
                templates: {
                    suggestion: function (x) {
                        return '<p>${x.name}</p>';
                    },
                    empty: null,
                    footer: null,
                    header: null
                }
            });
        }

        return {
            navigation: navigation,
            invalidate: function (container: Element) {
                Service.instance.getDataset(id).then(function (x) {
                    var root = new JqHtml(container);

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
                                files: { },
                                ownerships: { },
                            }
                        }
                    };
                    trace(data);
                    var binding = JsViews.objectObservable(data);

                    View.getTemplate("dataset/edit").link(root, data);
                    setAttributeTypeahead(root);
                    setOwnerTypeahead(root);

                    root.find("#dataset-finish-editing").on("click", function (_) {
                        navigation.update(PageNavigation.Navigate(Page.DatasetShow(id)));
                    });

                    root.find("#dataset-attribute-add").on("click", function (_) {
                        removeAttributeTypeahead(root);
                        var attrs = JsViews.arrayObservable(data.dataset.meta.attributes);
                        attrs.insert({ name: "", value:"" });
                        setAttributeTypeahead(root);
                    });

                    root.on("click", ".dataset-attribute-remove", function (e) {
                        removeAttributeTypeahead(root);
                        var index = new JqHtml(e.target).data("value");
                        var attrs = JsViews.arrayObservable(data.dataset.meta.attributes);
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

                    root.find("#dataset-file-add-form").on("change", "input[type=file]", function (e) {
                        if (new JqHtml(e.target).val() != "") {
                            root.find("#dataset-file-add-submit").show();
                        } else {
                            root.find("#dataset-file-add-submit").hide();
                        }
                    });
                    root.find("#dataset-file-add-submit").on("click", function (_) {
                        Service.instance.addDatasetFiles(id, root.find("#dataset-file-add-form")).then(function (res) {
                            root.find("#dataset-file-add-submit").hide();
                            JsViews.arrayObservable(data.dataset.files).insert(res[0]);
                        });
                    });
                    root.on("click", ".dataset-file-edit-start", function (e) {
                        var fid: String = new JqHtml(e.target).data("value");
                        var file = data.dataset.files.filter(function (x) return x.id == fid)[0];
                        var d = { name: file.name, description: file.description };

                        var target = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-edit");
                        var menu = new JqHtml(e.target).parents(".dataset-file").find(".dataset-file-menu");

                        var tpl = JsViews.template(Resource.getString("share/dataset/file/edit"));
                        tpl.link(target, d);
                        menu.hide();

                        function close() {
                            target.empty();
                            menu.show();
                            tpl.unlink(target);
                            target.off();
                        }

                        target.on("click", ".dataset-file-edit-submit", function (_) {
                            Service.instance.updateDatatetFileMetadata(id, fid, d.name, d.description).then(function (res) {
                                var fb = JsViews.objectObservable(file);
                                fb.setProperty("name", res.name);
                                fb.setProperty("description", res.description);
                                fb.setProperty("url", res.url);
                                fb.setProperty("size", res.size);
                                fb.setProperty("createdAt", res.createdAt);
                                fb.setProperty("createdBy", res.createdBy);
                                fb.setProperty("updatedAt", res.updatedAt);
                                fb.setProperty("updatedBy", res.updatedBy);
                                close();
                            });
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
                            Service.instance.replaceDatasetFile(id, fid, target.find("form")).then(function (res) {
                                var fb = JsViews.objectObservable(file);
                                fb.setProperty("name", res.name);
                                fb.setProperty("description", res.description);
                                fb.setProperty("url", res.url);
                                fb.setProperty("size", res.size);
                                fb.setProperty("createdAt", res.createdAt);
                                fb.setProperty("createdBy", res.createdBy);
                                fb.setProperty("updatedAt", res.updatedAt);
                                fb.setProperty("updatedBy", res.updatedBy);
                                close();
                            });
                        });

                        target.on("click", ".dataset-file-replace-cancel", function (_) {
                            close();
                        });
                    });
                    root.on("click", ".dataset-file-delete", function (e) {
                        var fid: String = new JqHtml(e.target).data("value");
                        JsTools.confirm("can delete?").bind(function (_) {
                            return Service.instance.removeDatasetFile(id, fid);
                        }).then(function (_) {
                            var files = data.dataset.files.filter(function (x) return x.id != fid);
                            JsViews.arrayObservable(data.dataset.files).refresh(files);
                        });
                    });

                    root.find("#dataset-icon-form").on("change", "input[type=file]", function (e) {
                        if (new JqHtml(e.target).val() != "") {
                            root.find("#dataset-icon-submit").show();
                        } else {
                            root.find("#dataset-icon-submit").hide();
                        }
                    });
                    root.find("#dataset-icon-submit").on("click", function (_) {
                        Service.instance.changeDatasetImage(id, JQuery.find("#dataset-icon-form")).then(function (res) {
                            var img = res.images.filter(function (x) return x.id == res.primaryImage)[0];
                            binding.setProperty("dataset.primaryImage.id", img.id);
                            binding.setProperty("dataset.primaryImage.url", img.url);
                            root.find("#dataset-icon-form input[type=file]").val("");
                            root.find("#dataset-icon-submit").hide();
                        });
                    });

                    root.find("#dataset-ownership-submit").on("click", function (_) {
                        Service.instance.updateDatasetACL(id, data.dataset.ownerships.map(function (x) {
                            return {
                                id: x.id,
                                type: x.ownerType,
                                accessLevel: x.accessLevel
                            }
                        }));
                    });

                    root.find("#dataset-guest-access-submit").on("click", function (_) {
                        Service.instance.setDatasetGuestAccessLevel(id, data.dataset.defaultAccessLevel);
                    });
                });
            },
            dispose: function () {
            }
        }
    }

    static function createAttributeBloodhound() {
        var attrbuteEngine = new Bloodhound({
            datumTokenizer: Bloodhound.tokenizers.obj.whitespace("value"),
            queryTokenizer: Bloodhound.tokenizers.whitespace,
            remote: {
                url: "/api/suggests/attributes",
                replace: function (url, query) {
                    return '$url?query=$query';
                },
                filter: function (x: {status: String, data: Array<String>}) {
                    return (x.status == "OK") ? x.data.map(function (x) return {value: x}) : [];
                }
            }
        });
        attrbuteEngine.initialize();
        return attrbuteEngine;
    }

    static function createOwnerBloodhound() {
        var ownerEngine = new Bloodhound({
            datumTokenizer: Bloodhound.tokenizers.obj.whitespace("name"),
            queryTokenizer: Bloodhound.tokenizers.whitespace,
            remote: {
                url: "/api/suggests/users_and_groups",
                replace: function (url, query) {
                    return '$url?query=$query';
                },
                filter: function (x: {status: String, data: Array<Dynamic>}) {
                    return (x.status == "OK") ? x.data : [];
                }
            }
        });
        ownerEngine.initialize();
        return ownerEngine;
    }
}