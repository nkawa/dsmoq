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

		var rootBinding = JsViews.observable({ data: dsmoq.Async.Pending });
		View.getTemplate("dashboard/show").link(html, rootBinding.data());

		//TODO パフォーマンス的に問題がある
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
		
		Service.instance.getTags().then(function(x) {
			var data = {
				isGuest: profile.isGuest,
				featuredDatasets: Async.Pending,
				recentDatasets: Async.Pending,
				myDatasets: Async.Pending,
				myGroups: Async.Pending,
				statistics: Async.Pending,
				tag: x,
				message: Async.Pending
			};

            rootBinding.setProperty("data", data);
            var binding = JsViews.observable(rootBinding.data().data);
			
			Service.instance.findDatasets( { attributes: [ { name: "featured", value: "" } ], limit: 10, orderby: "attribute" } ).then(function(x) {
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
			
			Service.instance.getMessage().then(function(x) {
				binding.setProperty("message", Async.Completed(x));
			});
			
		});
		
        return new Promise(function (_) { });
    }

}