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

        var data = {
            isGuest: profile.isGuest,
			featuredDatasets: Async.Pending,
            recentDatasets: Async.Pending,
            myDatasets: Async.Pending,
            myGroups: Async.Pending,
			statistics: Async.Pending,
        };

        var binding = JsViews.observable(data);
        View.getTemplate("dashboard/show").link(html, data);

		function ellipseLongDescription() {
			JQuery._(".description").each(function(i: Int, e: Element) { 
				var target = JQuery._(e);
				var html = target.html();
				var clone = target.clone();
				clone.css( { display: "none", position: "absolute", overflow: "visible" } ).width(target.width()).height("auto");
				
				target.after(clone);
				
				while ((html.length > 0) && (clone.height() > target.height())) {
					html = html.substr(0, html.length - 1);
					clone.html(html + "...");
				}
				target.html(clone.html());
				clone.remove();
			} );
		}
		
		Service.instance.findDatasets( { attributes: [ { name: "featured", value: "" } ], limit: 4 } ).then(function(x) {
			if (x.results.length > 0) {
				x.results.sort(function(x: DatasetSummary, y: DatasetSummary): Int {
					var featured1 = x.attributes.filter(function(da: DatasetAttribute) {
						return da.name == "featured";
					});
					var featured2 = y.attributes.filter(function(da: DatasetAttribute) {
						return da.name == "featured";
					});
					featured1.sort(function (x1, y1) {
						var x1Value = Std.parseInt(x1.value);
						var y1Value = Std.parseInt(y1.value);
						if (x1Value == y1Value) {
							return 0;
						}
						if (x1Value == null && y1Value != null || x1Value > y1Value) {
							return 1;
						}
						return -1;
					});
					featured2.sort(function (x1, y1) {
						var x1Value = Std.parseInt(x1.value);
						var y1Value = Std.parseInt(y1.value);
						if (x1Value == y1Value) {
							return 0;
						}
						if (x1Value == null && y1Value != null || x1Value > y1Value) {
							return 1;
						}
						return -1;
					});
					var fx = featured1[0];
					var fy = featured2[0];
					var xValue = Std.parseInt(fx.value);
					var yValue = Std.parseInt(fy.value);
					if (xValue == yValue) {
						return 0;
					}
					if (xValue == null && yValue != null || xValue < yValue) {
						return -1;
					}
					return 1;
				});
			}
			binding.setProperty("featuredDatasets", Async.Completed(x.results));
			ellipseLongDescription();
		});
		
        Service.instance.findDatasets({ limit: 4 }).then(function (x) {
            binding.setProperty("recentDatasets", Async.Completed(x.results));
			ellipseLongDescription();
        });

        if (!profile.isGuest) {
            Service.instance.findDatasets({owners: [profile.name], limit: 4}).then(function (x) {
                binding.setProperty("myDatasets", Async.Completed(x.results));
				ellipseLongDescription();
            });
        }
		
		Service.instance.getStatistics({ }).then(function(x) {
			binding.setProperty("statistics", Async.Completed(x));
		});
		
        return new Promise(function (_) { });
    }

}