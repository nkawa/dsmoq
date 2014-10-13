package dsmoq.pages;

import js.html.Element;
import hxgnd.js.jsviews.JsViews;
import hxgnd.js.Html;
import dsmoq.models.Service;
import dsmoq.Async;
import hxgnd.Promise;
import hxgnd.Unit;
import conduitbox.Navigation;

class DashboardPage {

    public static function render(html: Html, onClose: Promise<Unit>): Promise<Navigation<Page>> {
        var profile = Service.instance.profile;

        var data = {
            isGuest: profile.isGuest,
            recentDatasets: Async.Pending,
            myDatasets: Async.Pending,
            myGroups: Async.Pending,
        };

        var binding = JsViews.observable(data);
        View.getTemplate("dashboard/show").link(html, data);

        Service.instance.findDatasets({ limit: 3 }).then(function (x) {
            binding.setProperty("recentDatasets", Async.Completed(x.results));
        });

        if (!profile.isGuest) {
            Service.instance.findDatasets({owner: [profile.id], limit: 3}).then(function (x) {
                binding.setProperty("myDatasets", Async.Completed(x.results));
            });
            Service.instance.findGroups({user: profile.id, limit: 3}).then(function (x) {
                binding.setProperty("myGroups", Async.Completed(x.results));
            });
        }

        return new Promise(function (_) { });
    }

}