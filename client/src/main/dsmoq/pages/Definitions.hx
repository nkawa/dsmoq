package dsmoq.pages;

import dsmoq.framework.Types;
import promhx.Promise;
import dsmoq.framework.JQuery.*;
import dsmoq.components.Clickable;

import dsmoq.framework.helpers.*;
import dsmoq.framework.Effect;
import dsmoq.components.Pagination;

import dsmoq.framework.Types;
import js.JQuery.JQueryHelper.*;
import dsmoq.framework.Effect;
import dsmoq.framework.JQuery.*;

using dsmoq.framework.helpers.Components;
import dsmoq.framework.helpers.*;

import dsmoq.components.ConnectionPanel;
import dsmoq.components.Header;

import dsmoq.pages.Api;
import dsmoq.pages.Models;

enum Page {
    DashBoard;

    DatasetList(paging: Option<PagingRequest>);
    DatasetNew;
    DatasetRead(id: String);
    DatasetEdit(id: String);

    GroupList;
    GroupRead;
    GroupEdit;

    Profile;
}

class Definitions {
    public static function application() {

        function toUrl(page: Page): PageInfo {
            return switch(page){
                case DashBoard:             Address.url("/");
                case DatasetRead(id):       Address.url('/datasets/read/$id');
                case DatasetEdit(id):       Address.url('/datasets/edit/$id');
                case DatasetList(paging):   Address.url('/datasets/list/', paging);
                case DatasetNew:            Address.url('/datasets/new/');
                case GroupList:             Address.url('/groups/list/');
                case GroupRead:             Address.url('/groups/read/');
                case GroupEdit:             Address.url('/groups/edit/');
                case Profile:               Address.url('/profile/');
            }
        }

        function draw(page: Page) {
            var body = switch(page){
                case DashBoard:                 Stub.render("DashBoard");
                case DatasetRead(id):           DatasetReadView.render(id);
                case DatasetEdit(id):           DatasetEditView.render(Some(id));
                case DatasetNew:                DatasetEditView.render(None);
                case DatasetList(paging):       DatasetListView.render(Core.getOrElse(paging, Pagination.top));
                case GroupList:                 Stub.render("GroupList");
                case GroupRead:                 Stub.render("GroupRead");
                case GroupEdit:                 Stub.render("GroupEdit");
                case Profile:                   Stub.render("Profile");
            }
            return { html: body.html, state: Core.nop, event: body.event };
        }

        return {
            initialize: function (page: Page): Replacable<Page, Void, Void> {
                var selector = "#main";

                var container = null; //微妙なんだけどとりあえず。

                function changePage(page: Page) {
                    Effect.global().changeUrl(toUrl(page), false);
                }

                function layout() {
                    function makePageFoldable(f: Page -> Rendered<Void, Page>) {
                        return Components.toComponent(f).outMap(Inner);
                    }

                    function render(login: LoginStatus) {
                        var header = Header.create().render(login);
                        container = PlaceHolders.withSideEffect("page-body", makePageFoldable(draw), changePage).render(page);
            //            header.event.then(pageBody.put)                   // TODO: promise can be called only once
                        var notification = div().attr("id", "notification");
                        var body = div().append(header.html).append(notification).append(container.html.addClass("container"));
                        return {html: body, state: Core.nop, event: Promises.void()};
                    }


                    return Components.toComponent(render);
                }

                function login() {
                    var ret = ConnectionPanel.request(
                        function (jq: Html) { return jq.text("waiting..."); },
                        "application",
                        Components.inMap(layout(), Api.extractProfile),
                        Effect.global().notifyError.bind(_, null)
                    ).render(Api.profile);

                    j(selector).append(ret.html);
                }

                login();

                return container;
            },

            toUrl: toUrl,
            fromUrl: function(url: PageInfo) {
                return switch(url.path.split("/").filter(function (x) {return x != "";})){
                    case ["datasets", "read", id]:  DatasetRead(id);
                    case ["datasets", "edit", id]:  DatasetEdit(id);
                    case ["datasets", "list"]:      DatasetList(untyped Address.hash(url));  // TODO: how type it?
                    case ["datasets", "new"]:       DatasetNew;
                    case ["groups", "list"]:        GroupList;
                    case ["groups", "read"]:        GroupRead;
                    case ["groups", "edit"]:        GroupEdit;
                    case ["profile"]:               Profile;
                    case _:                         DashBoard;
                }
            },
            draw: draw
        };
    }
}
