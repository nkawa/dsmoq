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

class TopPage {

    public static function render(html: Html, onClose: Promise<Unit>): Promise<Navigation<Page>> {
		var rootBinding = JsViews.observable({ data: dsmoq.Async.Pending });
		View.getTemplate("top/show").link(html, rootBinding.data());
		
		Service.instance.getTags().then(function(x) {
			var data = {
				featuredDatasets: Async.Pending,
				recentDatasets: Async.Pending,
				tag: x
			};

            rootBinding.setProperty("data", data);
            var binding = JsViews.observable(rootBinding.data().data);
			
			Service.instance.findDatasets( { attributes: [ { name: "featured", value: "" } ], limit: 10, orderby: "attribute" } ).then(function(x) {
				binding.setProperty("featuredDatasets", Async.Completed(x.results));
				var slider: Dynamic = untyped __js__('$("#feature")');
				slider.find("script").remove();
				var sliderPro = slider.sliderPro({ 
					width: "100%", 
					height: 450, 
					buttons: false, 
					thumbnailWidth: 200, 
					thumbnailHeight: 150, 
					thumbnailPointer: true, 
					fade: true, 
					arrows: true,
					keyboard: false,
					touchSwipe: false
				});
			});
			
			Service.instance.findDatasets({ limit: 12 }).then(function(x) {
				binding.setProperty("recentDatasets", Async.Completed(x.results));
				JQuery._(".dataset-title").each(function(i, dom) {
					var el = JQuery._(dom);
					var s = el.text();
					if (s.length > 54) {
						el.text(s.substring(0, 54) + "...");
					}
				});
			});
		});
        return new Promise(function(_) { });
    }
}
