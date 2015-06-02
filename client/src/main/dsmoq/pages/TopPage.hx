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
			});
			
			JQuery._("#main").css({ width: "100%" });
		});
		
		onClose.then(function(_) {
			JQuery._("#main").css({ width: "" });
		});
		
        return new Promise(function(_) { });
    }
}