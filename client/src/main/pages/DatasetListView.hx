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
import framework.helpers.Connection;
import pages.Api;
import pages.Models;

class DatasetListView{
    public static function render(paging: PagingRequest):Rendered<Void, Page>{
        function toViewPage(dataset: DatasetSummary){
            return DatasetRead(dataset.id);
        }
        function changeHash(req: PagingRequest){
            framework.Effect.global().updateHash(req);
        }

        var summaryComponent = Clickable.create(Templates.create("DatasetSummary"), "a")
            .state(Core.ignore)
            .emitInput()
            .outMap(toViewPage);
        var list = Common.observe(
                List.withPagination(Common.waiting,"list-sample", summaryComponent, Core.tap(Api.sendDatasetsList, changeHash)));
        return list.render(paging);
    }
}
