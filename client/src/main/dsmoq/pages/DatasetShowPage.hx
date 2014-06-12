package dsmoq.pages;

import dsmoq.Async;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.View;
import dsmoq.models.DatasetGuestAccessLevel;
import dsmoq.models.DatasetPermission;
import dsmoq.models.Service;
import js.Browser;
import js.html.Element;
import js.jqhx.JqHtml;
import js.jsviews.JsViews;
import js.support.ControllableStream;
import js.support.Promise;
import js.support.Unit;
import js.support.JsTools;

using dsmoq.framework.helper.JQueryTools;

class DatasetShowPage {
    public static function create(id: String) {
        var navigation = new ControllableStream();
        return {
            navigation: navigation,
            invalidate: function (container: Element) {
                var data = { data: Async.Pending };
                var binding = JsViews.objectObservable(data);
                View.getTemplate("dataset/show").link(container, data);

                Service.instance.getDataset(id).then(function (res) {
                    binding.setProperty("data", Async.Completed({
                        name: res.meta.name,
                        description: res.meta.description,
                        primaryImage: res.primaryImage,
                        ownerships: res.ownerships.filter(function (x) return Type.enumEq(x.accessLevel, DatasetPermission.Write)),
                        files: res.files,
                        attributes: res.meta.attributes,
                        license: res.meta.license,
                        isPrivate: Type.enumEq(res.defaultAccessLevel, DatasetGuestAccessLevel.Deny),
                        canEdit: Type.enumEq(res.permission, DatasetPermission.Write),
                        canDownload: switch (res.permission) {
                            case DatasetPermission.Write, DatasetPermission.Read: true;
                            case _: false;
                        }
                    }));

                    new JqHtml(container).find("#dataset-edit").on("click", function (_) {
                        navigation.update(PageNavigation.Navigate(Page.DatasetEdit(id)));
                    });

                    new JqHtml(container).find("#dataset-delete").createEventStream("click").chain(function (_) {
                        return JsTools.confirm("Are you sure you want to delete this dataset?");
                    }, function (_) return None).chain(function (_) {
                        return Service.instance.deleteDeataset(id);
                    }).then(function (_) {
                        // TODO 削除対象データセット閲覧履歴（このページ）をHistoryから消す
                        navigation.update(PageNavigation.Navigate(Page.DatasetList(1)));
                    });
                }, function (err) {
                    switch (err.name) {
                        case ServiceErrorType.Unauthorized:
                            container.innerHTML = "Permission denied";
                            trace("UnauthorizedError");
                        case _:
                            // TODO 通信エラーが発生しましたメッセージと手動リロードボタンを表示
                            container.innerHTML = "network error";
                            trace(err);
                    }
                });
            },
            dispose: function () {
            }
        }
    }
}