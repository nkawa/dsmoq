package pages;

import framework.Types;
import promhx.Promise;
import framework.JQuery.*;
import components.Clickable;
import pages.DashBoardView;

import framework.helpers.*;
import framework.Effect;
import components.Pagination;

enum Page { DashBoard; 
    DatasetList(paging: Option<PagingRequest>); DatasetNew; DatasetRead(id: String); DatasetEdit(id: String); 
    GroupList; Profile; }

class Definitions{
    public static function application(){
        return {
            toUrl: function(page: Page):PageInfo{
                return switch(page){
                    case DashBoard:             Address.url("/");
                    case DatasetRead(id):       Address.url('/datasets/read/$id');
                    case DatasetEdit(id):       Address.url('/datasets/edit/$id');
                    case DatasetList(paging):   Address.url('/datasets/list/', paging);
                    case GroupList:             Address.url('/groups/list/');
                    case DatasetNew:            Address.url('/datasets/new/');
                    case Profile:               Address.url('/profile/');
                }
            },
            fromUrl: function(url: PageInfo){
                return switch(url.path.split("/").filter(function(x){return x != "";})){
                    case ["datasets", "read", id]:    DatasetRead(id);
                    case ["datasets", "edit", id]:    DatasetEdit(id);
                    case ["datasets", "list"]:      DatasetList(untyped Address.hash(url));  // TODO: how type it?
                    case ["groups", "list"]:        GroupList;
                    case ["datasets", "new"]:       DatasetNew;
                    case ["profile"]:               Profile;
                    case _:                         DashBoard;
                }
            },
            draw: function(page: Page){
                var body = switch(page){
                    case DashBoard:                 DashBoardView.render();
                    case DatasetRead(id):           DatasetReadView.render(id);
                    case DatasetEdit(id):           DatasetEditView.render(Some(id));
                    case DatasetNew:                DatasetEditView.render(None);
                    case DatasetList(paging):       DatasetListView.render(Core.getOrElse(paging, Pagination.top));
                    case GroupList:                 DashBoardView.render();
                    case Profile:                   DashBoardView.render();
                }
                return { html: body.html, state: Core.nop, event: body.event };
            }
        };
    }
}
