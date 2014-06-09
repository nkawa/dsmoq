package dsmoq.pages;

import dsmoq.framework.types.PageContent;
import js.support.ControllableStream;
import js.html.Element;
import js.jsviews.JsViews;
import dsmoq.models.Service;
import dsmoq.framework.View;

class DashboardPage {

    public static function create(): PageContent<Page> {
        return {
            navigation: new ControllableStream(),
            invalidate: function (container: Element) {
                return {
                    navigation: new ControllableStream(),
                    invalidate: function (container: Element) {
                        var profile = Service.instance.profile;

                        var data = {
                            isGuest: profile.isGuest,

                            isRecentDatasetsLoading: true,
                            recentDatasets: [],

                            isMyDatasetsLoading: true,
                            myDatasets: [],

                            isMyGroupsLoading: true,
                            myGroups: []
                        };

                        var binding = JsViews.objectObservable(data);
                        View.getTemplate("dashboard/show").link(container, data);

                        Service.instance.findDatasets({ limit: 3 }).then(function (x) {
                            binding.setProperty("isRecentDatasetsLoading", false);
                            JsViews.arrayObservable(data.recentDatasets).refresh(x.results);
                        });

                        if (!profile.isGuest) {
                            Service.instance.findDatasets({owner: profile.id, limit: 3}).then(function (x) {
                                binding.setProperty("isMyDatasetsLoading", false);
                                JsViews.arrayObservable(data.myDatasets).refresh(x.results);
                            });
                            Service.instance.findGroups({user: profile.id, limit: 3}).then(function (x) {
                                binding.setProperty("isMyGroupsLoading", false);
                                JsViews.arrayObservable(data.myGroups).refresh(x.results);
                            });
                        }
                    },
                    dispose: function () {
                    }
                }
            },
            dispose: function () {}
        }
    }

}