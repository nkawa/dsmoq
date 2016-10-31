package dsmoq.pages;

import dsmoq.models.DatasetAttribute;
import dsmoq.models.DatasetSummary;
import js.html.Element;
import hxgnd.js.jsviews.JsViews;
import hxgnd.js.Html;
import dsmoq.models.Service;
import dsmoq.Async;
import hxgnd.Promise;
import hxgnd.Unit;
import conduitbox.Navigation;
import hxgnd.js.JQuery;
import js.Browser;
import js.html.Location;

class DashboardPage {

    public static function render(html: Html, onClose: Promise<Unit>): Promise<Navigation<Page>> {
        var profile = Service.instance.profile;
        if (profile.isGuest) {
            js.Browser.location.href = "/";
            return new Promise(function (_) { });
        }
        var rootBinding = JsViews.observable({ data: dsmoq.Async.Pending });
        View.getTemplate("dashboard/show").link(html, rootBinding.data());

        Service.instance.getTagColors().then(function(tags) {
            Service.instance.getMessage().then(function(message) {
                var data = {
                    isGuest: profile.isGuest,
                    myDatasets: Async.Pending,
                    message: message,
                    tag: tags
                };
                rootBinding.setProperty("data", data);
                var binding = JsViews.observable(rootBinding.data().data);
                Service.instance.findDatasets({owners: [profile.name], limit: 12}).then(function (dataset) {
                    binding.setProperty("myDatasets", Async.Completed(dataset.results));
                    JQuery._(".dataset-title").each(function(i, dom) {
                        var el = JQuery._(dom);
                        var s = el.text();
                        if (s.length > 54) {
                            el.text(s.substring(0, 54) + "...");
                        }
                    });
                });
            });
        });
        return new Promise(function (_) { });
    }
}
