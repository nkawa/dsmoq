package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.Async;
import dsmoq.models.Service;
import dsmoq.models.TagDetail;
import dsmoq.Page;
import hxgnd.js.Html;
import hxgnd.js.JsTools;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Unit;
using dsmoq.JQueryTools;

class GroupShowPage {
    public static function render(root: Html, onClose: Promise<Unit>, id: String): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();

        var rootBinding = JsViews.observable({ data: Async.Pending });
        View.getTemplate("group/show").link(root, rootBinding.data());

        Service.instance.getGroup(id).then(function (res) {
            var data = {
                myself: Service.instance.profile,
                group: res,
                members: Async.Pending,
                datasets: Async.Pending,
				tag: new Array<TagDetail>()
            };
            var binding = JsViews.observable(data);
            rootBinding.setProperty("data", data);

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
                    Service.instance.getGroupMembers(id, {offset: 20 * i}).then(function (x) {
                        var b = JsViews.observable(members);
                        b.setProperty("index", i);
                        b.setProperty("total", x.summary.total);
                        b.setProperty("items", x.results);
                        b.setProperty("pages", Math.ceil(x.summary.total / 20));
                    }, function (e) {
                        Notification.show("error", "error happened");
                    });
                });
            }, function (err) {
                Notification.show("error", "error happened");
            });

			Service.instance.getTags().then(function(x) {
				binding.setProperty("tag", x);
				Service.instance.findDatasets({groups: [res.name]}).then(function (x) {
					var datasets = {
						index: Math.ceil(x.summary.offset / 20),
						total: x.summary.total,
						items: x.results,
						pages: Math.ceil(x.summary.total / 20)
					};
					binding.setProperty("datasets", Async.Completed(datasets));

					JsViews.observe(datasets, "index", function (_, _) {
						var i = datasets.index;
						Service.instance.findDatasets({groups: [res.name], offset: 20 * i}).then(function (x) {
							var b = JsViews.observable(datasets);
							b.setProperty("index", i);
							b.setProperty("total", x.summary.total);
							b.setProperty("items", x.results);
							b.setProperty("pages", Math.ceil(x.summary.total / 20));
						}, function (e) {
							Notification.show("error", "error happened");
						});
					});
				}, function (err) {
					Notification.show("error", "error happened");
				});
            }, function (err) {
                Notification.show("error", "error happened");				
			});


            root.find("#group-edit").on("click", function (_) {
                navigation.fulfill(Navigation.Navigate(GroupEdit(id)));
            });

            root.find("#group-delete").createEventStream("click").flatMap(function (_) {
                return JsTools.confirm("Are you sure you want to delete this group?", false);
            }).then(function (_) {
                Service.instance.deleteGroup(id).then(function (_) {
                    Notification.show("success", "delete successful");
                    navigation.fulfill(Navigation.Navigate(Page.GroupList(1, "")));
                }, function (err) {
                    Notification.show("error", "error happened");
                });
            });
        }, function (err) {
            trace(err);
            root.html(switch (err.name) {
                case ServiceErrorType.Unauthorized:
                    "Permission denied";
                case ServiceErrorType.NotFound:
                    "Not found";
                case _:
                    "Network Error";
            });
        });

        return navigation.promise;
    }
}