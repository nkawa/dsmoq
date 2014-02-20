package pages;

import framework.Types;
import promhx.Promise;
import framework.JQuery.*;
import components.Clickable;
import pages.DashBoardView;

import framework.helpers.*;

enum Page { DashBoard; DatasetRead(id: String);}

class Definitions{
    public static function application(){
        return {
            toUrl: function(page: Page){
                return switch(page){
                    case DashBoard: "/";
                    case DatasetRead(id): '/dataset/$id';
                }
            },
            fromUrl: function(url:String){
                return switch(url.split("/").filter(function(x){return x != "";})){
                    case [""]: DashBoard;
                    case ["", "dataset", id]: DatasetRead(id);
                    case ["dataset", id]: DatasetRead(id);
                    case _: DashBoard;
                }
            },
            draw: function(page: Page){
                var body = switch(page){
                    case DashBoard:   DashBoardView.render();
                    case DatasetRead(id): DatasetReadView.render(id);
                }
                return { html: body.html, state: Core.nop, event: body.event };
            }
        };
    }
}
