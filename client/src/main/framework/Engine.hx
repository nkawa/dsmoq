package framework;

import framework.Types;
import js.JQuery.JQueryHelper.*;
import pushstate.PushState;
import framework.JQuery.*;

using framework.helpers.Components;
import framework.helpers.*;

class Engine<Page>{
    var currentPage:Page;
    var application: Application<Page>;
    var container: Replacable<Page, Void, Void>;
    var selector: String;
    var initialAccess:Bool = true;

    public function new(_application, initialPage, placeSelector = "#main"){
        application = _application;
        currentPage = initialPage;
        selector = placeSelector;
    }

    public function start(){
        PushState.init();
        PushState.addEventListener(onUrlChange);
        container = PlaceHolders.withSideEffect("container", makePageFoldable(application.draw), changePage).render(currentPage);
        j(selector).append(container.html);
    }

    private function makePageFoldable(f: Page -> Rendered<Void, Page>){
        return Components.toComponent(f).outMap(Inner);
    }

    private function changePage(page:Page){
        currentPage = page;
        if(initialAccess){
            initialAccess = false;
        }else{
            PushState.push(application.toUrl(page));
        }
    }

    private function onUrlChange(url){
        var newPage = application.fromUrl(url);
        if(! Type.enumEq(newPage, currentPage)) {
            container.put(newPage);
        }
    }
}
