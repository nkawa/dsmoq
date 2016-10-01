package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.models.ApiStatus;
import dsmoq.models.GroupImage;
import dsmoq.models.GroupMember;
import dsmoq.models.GroupRole;
import dsmoq.models.Service;
import dsmoq.models.User;
import dsmoq.View;
import dsmoq.views.ViewTools;
import haxe.ds.Option;
import haxe.Resource;
import hxgnd.js.Html;
import hxgnd.js.JQuery;
import hxgnd.js.JsTools;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Unit;
import js.bootstrap.BootstrapButton;
import js.html.Event;
import js.html.EventTarget;

using hxgnd.OptionTools;

class GroupEditPage {
    inline static var MemberCandidateSize = 5;
    inline static var ImageCandicateSize = 10;

    public static function render(root: Html, onClose: Promise<Unit>, id: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var rootData = { data: Async.Pending };
        var rootBinding = JsViews.observable(rootData);
        View.getTemplate("group/edit").link(root, rootData);

        Service.instance.getGroup(id).flatMap(function (x) {
            return switch (x.role) {
                case GroupRole.Manager:
                    Promise.fulfilled(x);
                case _:
                    Promise.rejected(new ServiceError("", ServiceErrorType.Unauthorized));
            }
        }).thenError(function (err: Dynamic) {
            root.html(err.responseJSON.status);
        }).then(function (res) {
            var data = {
                myself: Service.instance.profile,
                group: res,
                groupErrors: {description: ""},
                members: Async.Pending,
            };
            var binding = JsViews.observable(data);
            rootBinding.setProperty("data", Async.Completed(data));

            // CKEditor setting
            var editor = CKEditor.replace("description");
            editor.setData(data.group.description);
            editor.on("change", function(evt) {
                var text = editor.getData(false);
                binding.setProperty('group.description', text);
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
            
            
            function loadGroupMember() {
                Service.instance.getGroupMembers(id).then(function (x) {
                    var members = {
                        index: Math.ceil(x.summary.offset / 20),
                        total: x.summary.total,
                        items: x.results,
                        pages: Math.ceil(x.summary.total / 20)
                    };    
                    binding.setProperty("members", Async.Completed(members));
                    JsViews.observe(members, "index", function (_, _) {
                        var i = members.index;
                        Service.instance.getGroupMembers(id, { offset: 20 * i, limit: 20 } ).then(function (x) {
                            var b = JsViews.observable(members);
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

            loadGroupMember();

            // basics tab ------------------------
            root.find("#group-basics-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#group-basics-submit"));
                root.find("#group-basics").find("input,textarea").attr("disabled", true);
                Service.instance.updateGroupBasics(id, data.group.name, data.group.description).then(function (_) {
                    Notification.show("success", "save successful");
                }, function (err: Dynamic) {
                    switch (err.status) {
                        case 400: // BadRequest
                            switch (err.responseJSON.status) {
                                case ApiStatus.IllegalArgument:
                                    var name = StringTools.replace(err.responseJSON.data.key, "d\\.", "");
                                    binding.setProperty('groupErrors.${name}', StringTools.replace(err.responseJSON.data.value, "d.", ""));
                            }
                    }
                }, function () {
                    BootstrapButton.reset(root.find("#group-basics-submit"));
                    root.find("#group-basics").find("input,textarea").removeAttr("disabled");
                });
            });

            // icon tab -------------------------
            root.find("#group-icon-select").on("click", function (_) {
                showSelectImageDialog(id, binding).then(function(image) {
                    Service.instance.setGroupPrimaryImage(id, image.id).then(
                        function (_) {
                            binding.setProperty("group.primaryImage.id", image.id);
                            binding.setProperty("group.primaryImage.url", image.url);
                            Notification.show("success", "save successful");
                        }
                    );
                });                
            });
            
            // members tab ----------------------
            root.find("#group-members").on("click", "#add-member-menu-item", function (_) {
                showAddMemberDialog().then(function (members) {
                    ViewTools.showLoading("body");
                    Service.instance.addGroupMember(id, members).then(function (x) {
                        ViewTools.hideLoading("body");
                        loadGroupMember();
                        Notification.show("success", "save successful");
                    }, function (err) {
                        ViewTools.hideLoading("body");
                    });
                });
            });

            function getMemberByElement(target: EventTarget): Option<dsmoq.models.GroupMember> {
                var node = JQuery._(target);
                var index = node.parents("tr[data-index]").data("index");
                return switch (data.members) {
                    case Async.Completed(members):
                        OptionTools.toOption(members.items[index]);
                    case _:
                        Option.None;
                }
            }

            root.find("#group-members").on("change", ".dsmoq-role-select", function (e) {
                // bindingが更新タイミングの問題があるため、setImmediateを挟む
                JsTools.setImmediate(function () {
                    getMemberByElement(e.currentTarget).iter(function (member) {
                        Service.instance.updateGroupMemberRole(id, member.id, member.role).then(function (_) {
                            Notification.show("success", "save successful");
                        });
                    });
                });
            });

            root.find("#group-members").on("click", ".dsmoq-remove-button", function (e) {
                getMemberByElement(e.currentTarget).iter(function (member) {
                    ViewTools.showConfirm("Are you sure you want to remove?").then(function (isOk) {
                        if (isOk) {
                            ViewTools.showLoading("body");
                            Service.instance.removeGroupMember(id, member.id)
                                .flatMap(function (_) return Service.instance.getGroupMembers(id))
                                .then(function (x) {
                                    loadGroupMember();
                                    ViewTools.hideLoading("body");
                                    Notification.show("success", "remove successful");
                                }, function (err) {
                                    ViewTools.hideLoading("body");
                                });
                        }
                    });
                });
            });

            // ----------------------------------
            root.find("#group-finish-editing").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(Page.GroupShow(id)));
            });
        });

        return navigation.promise;
    }

    static function showAddMemberDialog() {
        var data = {
            query: "",
            offset: 0,
            hasPrev: false,
            hasNext: false,
            items: new Array<{selected: Bool, item: User}>(),
            selectedIds: new Array<String>(),
            isManager: false
        }
        var binding = JsViews.observable(data);
        var tpl = JsViews.template(Resource.getString("template/group/add_member_dialog"));

        return ViewTools.showModal(tpl, data, function (html, ctx) {
            function searchMemberCandidate(?query: String, offset = 0) {
                var limit = MemberCandidateSize + 1;
                Service.instance.findUsers({ query: query, offset: offset, limit: limit }).then(function (users) {
                    var list = users.slice(0, MemberCandidateSize)
                                    .map(function (x) return {
                                        selected: data.selectedIds.indexOf(x.id) >= 0,
                                        item: x
                                    });
                    var hasPrev = offset > 0;
                    var hasNext = users.length > MemberCandidateSize;
                    binding.setProperty("offset", offset);
                    binding.setProperty("hasPrev", hasPrev);
                    binding.setProperty("hasNext", hasNext);
                    JsViews.observable(data.items).refresh(list);
                });
            }

            function filterSelectedMember() {
                return data.items
                            .filter(function (x) return x.selected)
                            .map(function (x) return x.item);
            }

            JsViews.observable(data.items).observeAll(function (e, args) {
                // Memberのチェックボックスを選択/解除した時の動作
                if (args.path == "selected") {
                    var user: User = e.target.item;
                    var ids = data.selectedIds.copy();
                    var b = JsViews.observable(data.selectedIds);
                    // 選択した場合、管理情報にデータを追加
                    if (args.value) {
                        if (ids.indexOf(user.id) < 0) {
                            ids.push(user.id);
                            b.refresh(ids);
                        }
                    // 解除した場合、管理情報からデータを削除
                    } else {
                        if (ids.remove(user.id)) {
                            b.refresh(ids);
                        }
                    }
                    // 選択しているMemberの数を表示(更新)
                    html.find("#add-member-selected-count").text(data.selectedIds.length);
                }
            });

            binding.setProperty("query", "");
            JsViews.observable(data.selectedIds).refresh([]);
            searchMemberCandidate();

            html.find("#member-search-form").on("submit", function (e: Event) {
                e.preventDefault();
                searchMemberCandidate(data.query);
            });

            html.find("#member-list-prev").on("click", function (_) {
                var query = data.query;
                var offset = data.offset - MemberCandidateSize;
                searchMemberCandidate(query, offset);
            });

            html.find("#member-list-next").on("click", function (_) {
                var query = data.query;
                var offset = data.offset + MemberCandidateSize;
                searchMemberCandidate(query, offset);
            });

            html.on("click", "#add-member-dialog-submit", function (e) {
                var role = data.isManager ? GroupRole.Manager : GroupRole.Member;
                var members = data.selectedIds
                                .map(function (x) return { userId: x, role:role });
                ctx.fulfill(members);
            });
        });
    }
    
    /**
     * 画像選択ダイアログを表示する。
     *
     * @param id グループID
     * @param rootBinding
     * @return モーダルダイアログを表示するPromise
     */
    static function showSelectImageDialog(id: String, rootBinding: Observable) {
        var data = {
            offset: 0,
            hasPrev: false,
            hasNext: false,
            items: new Array<{selected: Bool, item: GroupImage}>(),
            selectedIds: new Array<String>()
        }
        var binding = JsViews.observable(data);
        var tpl = JsViews.template(Resource.getString("template/share/select_image_dialog"));
        
        return ViewTools.showModal(tpl, data, function (html, ctx) {
            function searchImageCandidate(offset = 0) {
                var limit = ImageCandicateSize + 1;
                Service.instance.getGroupImage(id, { offset: offset, limit: limit }).then(function (images) {
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
                    var image: GroupImage = e.target.item;
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
            
            html.find("#upload-image-form input").on("change", function(_) {
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
                Service.instance.addGroupImages(id, html.find("#upload-image-form")).then(
                    function (_) {
                        Notification.show("success", "save successful");
                        searchImageCandidate();
                        html.find("#upload-image-form input").val("");
                    },
                    function (e) {
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
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
                Service.instance.removeGroupImage(id, selected).then(
                    function (ids) {
                        Notification.show("success", "delete successful");
                        searchImageCandidate();
                        binding.refresh([]);
                        rootBinding.setProperty("group.primaryImage.id", ids.primaryImage);
                        rootBinding.setProperty("group.primaryImage.url", getUrl(ids.primaryImage));
                    },
                    function (e) {
                        // Service内でNotificationを出力するようにしたため、この箇所でのNotification出力は不要。
                        // このfunctionはfinally時に呼び出されるfunctionを指定するための引数の数合わせです。
                    },
                    function () {
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
