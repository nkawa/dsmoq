package dsmoq.pages;

import js.support.PositiveInt;
import js.support.ControllableStream;
import js.html.Element;
import dsmoq.models.Service;
import js.jsviews.JsViews;
import dsmoq.framework.View;

class GroupListPage {
    public static function create(page: PositiveInt) {
        return {
            navigation: new ControllableStream(),
            invalidate: function (container: Element) {
                var rootBinding = JsViews.objectObservable({
                    data: dsmoq.Async.Pending
                });
                View.getTemplate("group/list").link(container, rootBinding.data());

                Service.instance.findGroups().then(function (res) {
                    var data = { condition: { }, result: res };
                    rootBinding.setProperty("data", Async.Completed(data));
                });
            },
            dispose: function () {
            }
        }
    }
}