package pages;

import promhx.Promise;
import pages.Definitions;
import framework.Types;
import framework.helpers.*;
import framework.JQuery.*;

import components.Clickable;
using framework.helpers.Components;
import components.List;
import components.Pagination;

class DatasetListView{
    public static function render():Rendered<Void, Page>{
        function toViewPage(dataset: {id: String, title: String}){
            return DatasetRead(dataset.id);
        }
        function request(req: PagingRequest){
            function delay(promise, v){
                haxe.Timer.delay(function(){promise.resolve(v);}, 500);
            }
            var result = {
                paging: {num: req.num, numPerPage: req.numPerPage, total: 101}, 
                result: [for (i in req.num ... (req.num + req.numPerPage)) {id: Std.string(i), title: "title:"+Std.string(i)}]
            };

            return { event: Promises.tap(function(p){delay(p, result);}), state: Core.nop};
        }
        var summaryComponent = Clickable.create(Templates.create("DatasetSummary"), "a")
            .state(Core.ignore)
            .emitInput()
            .outMap(toViewPage);
        var list = List.withPagination(Common.waiting,"list-sample", summaryComponent, request);
        return list.render({num: 1, numPerPage: 10});
    }
}
