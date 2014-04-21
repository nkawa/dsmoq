package dsmoq.pages;

import dsmoq.framework.types.PageContainer;
import promhx.Promise;
import promhx.Stream;
import dsmoq.pages.Definitions.Page;
import dsmoq.framework.JQuery.*;
import dsmoq.framework.types.PageInfo;
import dsmoq.framework.types.Option;
import dsmoq.framework.types.Html;

import dsmoq.framework.JQuery.*;
import dsmoq.framework.helpers.*;
import js.JQuery.JQueryHelper.*;

import dsmoq.framework.Effect;
using dsmoq.framework.helpers.Components;

import dsmoq.components.ConnectionPanel;
import dsmoq.components.Header;

import dsmoq.pages.Api;
import dsmoq.pages.Models;
import dsmoq.components.Pagination;
import dsmoq.components.Clickable;
import dsmoq.framework.types.Replacable;
import dsmoq.framework.types.PageEvent;
import dsmoq.framework.types.Component;
import dsmoq.framework.types.PageComponent;

enum Page {
    DashBoard;

    DatasetList(paging: Option<PagingInfo>);
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
                case DashBoard:             Address.url("");
                case DatasetRead(id):       Address.url('datasets/read/$id');
                case DatasetEdit(id):       Address.url('datasets/edit/$id');
                case DatasetList(paging):   Address.url('datasets/list/', paging);
                case DatasetNew:            Address.url('datasets/new/');
                case GroupList:             Address.url('groups/list/');
                case GroupRead:             Address.url('groups/read/');
                case GroupEdit:             Address.url('groups/edit/');
                case Profile:               Address.url('profile/');
            }
        }

        function render(page: Page, oldHtml: Option<Html>) {
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
            initialize: function (): PageContainer<Page> {
                var selector = "#main";

                var container = divNamed("page-body").addClass("container");

                function putPage(component: Component<Void, PageEvent<Page>>) {
                    container.empty().append(component.html);
                }

                var mainContent = Components.toComponent(function (status: LoginStatus) {
                    var header = Header.create().render(status);
                    var notification = div().attr("id", "notification");
                    var body = div().append(header.html).append(notification).append(container);
                    return {html: body, state: Core.nop, event: header.event };
                });

                var panel = ConnectionPanel.request(
                    function (jq: Html) { return jq.text("waiting..."); },
                    "application",
                    Components.inMap(mainContent, Api.extractProfile),
                    Effect.global().notifyError.bind(_, null)
                ).render(Api.profile);

                j(selector).append(panel.html);

                return {
                    html: panel.html,
                    event: panel.event,
                    state: Core.nop,
                    put: putPage
                }
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
            render: render
        };
    }
}