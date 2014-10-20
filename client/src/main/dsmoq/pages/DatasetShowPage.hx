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
        var data = { data: Async.Pending };
        var binding = JsViews.observable(data);
        View.getTemplate("dataset/show").link(html, data);

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
                },
                accessCount: res.accessCount
            }));

            html.find("#dataset-edit").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(Page.DatasetEdit(id)));
            });

            html.find("#dataset-delete").createEventStream("click").flatMap(function (_) {
                return JsTools.confirm("Are you sure you want to delete this dataset?");
            }).flatMap(function (_) {
                return Service.instance.deleteDeataset(id);
            }).then(function (_) {
                // TODO 削除対象データセット閲覧履歴（このページ）をHistoryから消す
                navigation.fulfill(Navigation.Navigate(Page.DatasetList(1)));
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
}