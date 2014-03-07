package pages;

import framework.Types;
import promhx.Promise;
import framework.JQuery.*;
import components.Clickable;
import pages.DashBoardView;

import framework.helpers.*;

enum Page { DashBoard; 
    DatasetList; DatasetNew; DatasetRead(id: String); DatasetEdit(id: String); 
    GroupList; Profile; }

class Definitions{
    public static function application(){
        return {
            toUrl: function(page: Page){
                return switch(page){
                    case DashBoard:             "/";
                    case DatasetRead(id):       '/datasets/read/$id';
                    case DatasetEdit(id):       '/datasets/edit/$id';
                    case DatasetList:           '/datasets/list/';
                    case GroupList:             '/groups/list/';
                    case DatasetNew:            '/datasets/new/';
                    case Profile:               '/profile/';
                }
            },
            fromUrl: function(url:String){
                return switch(url.split("/").filter(function(x){return x != "";})){
                    case ["datasets", "read", id]:    DatasetRead(id);
                    case ["datasets", "edit", id]:    DatasetEdit(id);
                    case ["datasets", "list"]:      DatasetList;
                    case ["groups", "list"]:        GroupList;
                    case ["datasets", "new"]:       DatasetNew;
                    case ["profile"]:               Profile;
                    case _:                         DashBoard;
                }
            },
            draw: function(page: Page){
                var body = switch(page){
                    case DashBoard:         DashBoardView.render();
                    case DatasetRead(id):   DatasetReadView.render(id);
                    case DatasetEdit(id):   DatasetEditView.render(id);
                    case DatasetList:       DatasetListView.render();
                    case GroupList:         DashBoardView.render();
                    case DatasetNew:        DashBoardView.render();
                    case Profile:           DashBoardView.render();
                }
                return { html: body.html, state: Core.nop, event: body.event };
            }
        };
    }
}
