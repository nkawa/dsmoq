package framework;

import framework.Types;
import js.JQuery.JQueryHelper.*;
import pushstate.PushState;
import framework.JQuery.*;

using framework.helpers.Components;
import framework.helpers.*;

import components.ConnectionPanel;
import components.Header;

import pages.Auth;

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

        function initialLoading(jq:Html){
            return jq.text("waiting...");
        }
        var request = {url: Settings.api.login, json:{}};

        var login = ConnectionPanel.create(initialLoading, "application", 
            Components.inMap(layout(), Auth.extractUser)
        ).render(request);

        j(selector).append(login.html);
    }

    private function layout(){
        function render(login: LoginStatus){
            var header = Header.create().render(login);
            container = PlaceHolders.withSideEffect("page-body", makePageFoldable(application.draw), changePage).render(currentPage);
//            header.event.then(pageBody.put)                   // TODO: promise can be called only once
            var body = div().append(header.html).append(container.html.addClass("container"));
            return {html: body, state: Core.nop, event: Promises.void()};
        }
        return Components.toComponent(render);
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
            if(container == null){
                currentPage = newPage;
            }else{
                container.put(newPage);
            }
        }
    }
}
