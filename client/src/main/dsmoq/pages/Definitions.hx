package dsmoq.pages;

import dsmoq.framework.Types;
import promhx.Promise;
import dsmoq.framework.JQuery.*;
import dsmoq.components.Clickable;

import dsmoq.framework.helpers.*;
import dsmoq.framework.Effect;
import dsmoq.components.Pagination;

enum Page { DashBoard;
    DatasetList(paging: Option<PagingRequest>); DatasetNew; DatasetRead(id: String); DatasetEdit(id: String);
    GroupList; GroupRead; GroupEdit;
    Profile; }

class Definitions{
    public static function application(){
        return {
            toUrl: function(page: Page):PageInfo{
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
            },
            fromUrl: function(url: PageInfo){
                return switch(url.path.split("/").filter(function(x){return x != "";})){
                    case ["datasets", "read", id]:    DatasetRead(id);
                    case ["datasets", "edit", id]:    DatasetEdit(id);
                    case ["datasets", "list"]:      DatasetList(untyped Address.hash(url));  // TODO: how type it?
                    case ["datasets", "new"]:       DatasetNew;
                    case ["groups", "list"]:        GroupList;
                    case ["groups", "read"]:        GroupRead;
                    case ["groups", "edit"]:        GroupEdit;
                    case ["profile"]:               Profile;
                    case _:                         DashBoard;
                }
            },
            draw: function(page: Page){
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
        };
    }
}
