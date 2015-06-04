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
				myDatasets: Async.Pending,
				tag: x
			};

            rootBinding.setProperty("data", data);
            var binding = JsViews.observable(rootBinding.data().data);

			if (!profile.isGuest) {
				Service.instance.findDatasets({owners: [profile.name], limit: 12}).then(function (x) {
					binding.setProperty("myDatasets", Async.Completed(x.results));
					ellipseLongDescription();
				});
			}
			
		});
		
        return new Promise(function (_) { });
    }

}