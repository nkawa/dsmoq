package framework;

import framework.Types;
import js.JQuery.JQueryHelper.*;
import framework.Effect;
import framework.JQuery.*;

using framework.helpers.Components;
import framework.helpers.*;

import components.ConnectionPanel;
import components.Header;

import pages.Api;
import pages.Models;

class Engine<Page>{
    var application: Application<Page>;
    var container: Replacable<Page, Void, Void>;
    var selector: String;
    var currentPage: Page;

    public function new(_application, placeSelector = "#main"){
        application = _application;
        selector = placeSelector;
    }

    public function start(){
        Effect.initialize(renderPage);

        function initialLoading(jq:Html){
            return jq.text("waiting...");
        }

        var login = ConnectionPanel.request(initialLoading, "application", 
            Components.inMap(layout(), Api.extractProfile)
        ).render(Api.profile);

        j(selector).append(login.html);
    }

    private function renderPage(location: PageInfo){
        if(container != null){
            var newPage = application.fromUrl(location);
            if(!Type.enumEq(newPage, currentPage)){
                currentPage = newPage;
                container.put(currentPage);
            }
        }
    }

    private function layout(){
        function makePageFoldable(f: Page -> Rendered<Void, Page>){
            return Components.toComponent(f).outMap(Inner);
        }
        function render(login: LoginStatus){
            var header = Header.create().render(login);
            currentPage = application.fromUrl(Effect.global().location());
            container = PlaceHolders.withSideEffect("page-body", makePageFoldable(application.draw), changePage).render(currentPage);
//            header.event.then(pageBody.put)                   // TODO: promise can be called only once
            var notification = JQuery.div().attr("id", "notification");
            var body = div().append(header.html).append(notification).append(container.html.addClass("container"));
            return {html: body, state: Core.nop, event: Promises.void()};
        }
        return Components.toComponent(render);
    }


    private function changePage(page:Page){
        Effect.global().changeUrl(application.toUrl(page));
    }

}
