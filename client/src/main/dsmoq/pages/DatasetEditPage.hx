package dsmoq.pages;

import conduitbox.Navigation;
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
import js.Lib;

using hxgnd.OptionTools;

class DatasetEditPage {
    inline static var OwnerCandicateSize = 5;

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
                    files: x.files,
                    ownerships: x.ownerships,
                    defaultAccessLevel: x.defaultAccessLevel,
                    primaryImage: x.primaryImage,
					localState: x.localState,
					s3State: x.s3State,
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
			root.find("#dataset-attribute-import").click(function(_) {
				var element = Browser.document.getElementById("csv-file");
				var files = cast(element, InputElement).files;
				if (files.length == 0) {
					return;
				}
				var file = files.item(0);
				var fileReader = new FileReader();
				fileReader.readAsText(file, "UTF-8");
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
				};
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
						for (i in 0...res.length) {
							JsViews.observable(data.dataset.files).insert(res[i]);
						}
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
			function loadOwnerships() {
                Service.instance.getDataset(id).then(function (x) {
                    binding.setProperty("owners", Async.Completed({
                        index: 0,
                        total: x.ownerships.length,
                        items: x.ownerships,
                        pages: Math.ceil(x.ownerships.length / 20)
                    }));
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
                        Notification.show("error", "error happened");
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
                            Notification.show("error", "error happened");
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
                                    Notification.show("success", "save successful");
                                }, function (err) {
                                    ViewTools.hideLoading("body");
                                    Notification.show("error", "error happened");
                                });
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
                        Notification.show("error", "error happened");
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
                        Notification.show("error", "error happened");
                    },
                    function () {
                        BootstrapButton.reset(root.find("#dataset-storage-submit"));
                        root.find("#dataset-storage-form input").removeAttr("disabled");
                    }
                );
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
            selectedIds: new Array<String>()
        }
        var binding = JsViews.observable(data);
        var tpl = JsViews.template(Resource.getString("template/dataset/add_owner_dialog"));
        
        return ViewTools.showModal(tpl, data, function (html, ctx) {
            function searchOwnerCandidate(?query: String, offset = 0) {
                var limit = OwnerCandicateSize + 1;
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
			
            function filterSelectedOwner() {
                return data.items
                            .filter(function (x) return x.selected)
                            .map(function (x) return x.item);
            }

            JsViews.observable(data.items).observeAll(function (e, args) {
                if (args.path == "selected") {
                    var owner: SuggestedOwner = e.target.item;
                    var ids = data.selectedIds.copy();
                    var b = JsViews.observable(data.selectedIds);
                    if (args.value) {
                        if (ids.indexOf(owner.id) < 0) {
                            ids.push(owner.id);
                            b.refresh(ids);
                        }
                    } else {
                        if (ids.remove(owner.id)) {
                            b.refresh(ids);
                        }
                    }
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

            html.on("click", "#add-owner-dialog-submit", function (e) {
                ctx.fulfill(filterSelectedOwner());
            });
        });
    }
}