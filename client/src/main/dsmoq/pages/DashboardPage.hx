package dsmoq.pages;

import dsmoq.framework.types.PageContent;
import js.support.ControllableStream;
import js.html.Element;
import js.jsviews.JsViews;
import dsmoq.models.Service;
import dsmoq.framework.View;
import dsmoq.Async;

class DashboardPage {

    public static function create(): PageContent<Page> {
        return {
            navigation: new ControllableStream(),
            invalidate: function (container: Element) {
                var profile = Service.instance.profile;

                var data = {
                    isGuest: profile.isGuest,
                    recentDatasets: Async.Pending,
                    myDatasets: Async.Pending,
                    myGroups: Async.Pending,
                };

                var binding = JsViews.objectObservable(data);
                View.getTemplate("dashboard/show").link(container, data);

                Service.instance.findDatasets({ limit: 3 }).then(function (x) {
                    binding.setProperty("recentDatasets", Async.Completed(x.results));
                });

                if (!profile.isGuest) {
                    Service.instance.findDatasets({owner: profile.id, limit: 3}).then(function (x) {
                        binding.setProperty("myDatasets", Async.Completed(x.results));
                    });
                    Service.instance.findGroups({user: profile.id, limit: 3}).then(function (x) {
                        binding.setProperty("myGroups", Async.Completed(x.results));
                    });
                }
            },
            dispose: function () {
            }
        }
    }

}