package pages;

import framework.Types;
import promhx.Promise;
import framework.JQuery.*;
import components.Clickable;
import pages.DashBoardView;

import framework.helpers.*;

enum Page { DashBoard; DatasetRead(id: String);DatasetList; GroupList; DatasetNew; Profile; }

class Definitions{
    public static function application(){
        return {
            toUrl: function(page: Page){
                return switch(page){
                    case DashBoard:             "/";
                    case DatasetRead(id):       '/datasets/id/$id';
                    case DatasetList:           '/datasets/show/';
                    case GroupList:             '/groups/show/';
                    case DatasetNew:            '/datasets/new/';
                    case Profile:               '/profile/';
                }
            },
            fromUrl: function(url:String){
                return switch(url.split("/").filter(function(x){return x != "";})){
                    case ["datasets", "id", id]:    DatasetRead(id);
                    case ["datasets", "show"]:      DatasetList;
                    case ["groups", "show"]:        GroupList;
                    case ["datasets", "new"]:       DatasetNew;
                    case ["profile"]:               Profile;
                    case _:                         DashBoard;
                }
            },
            draw: function(page: Page){
                var body = switch(page){
                    case DashBoard:         DashBoardView.render();
                    case DatasetRead(id):   DatasetReadView.render(id);
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
