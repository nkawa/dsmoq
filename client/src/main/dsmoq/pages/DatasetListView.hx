package dsmoq.pages;

import promhx.Promise;
import dsmoq.pages.Definitions;
import dsmoq.framework.Types;
import dsmoq.framework.helpers.*;
import dsmoq.framework.JQuery.*;
import promhx.Stream.Stream;

import dsmoq.components.Clickable;
using dsmoq.framework.helpers.Components;
import dsmoq.components.List;
import dsmoq.components.Pagination;
import dsmoq.framework.helpers.Connection;
import dsmoq.pages.Api;
import dsmoq.pages.Models;

class DatasetListView {
    public static function render(paging: PagingInfo): Rendered<Void, Page> {
        function toViewPage(dataset: DatasetSummary) {
            return DatasetRead(dataset.id);
        }
        function changeHash(req: PagingInfo) {
            dsmoq.framework.Effect.global().updateHash(req);
        }
        function toViewModel(dataset: DatasetSummary) {
            return {
                name: dataset.name,
                dataSize: dataset.dataSize,
                description: dataset.description,
                files: dataset.files,
                uploadedBy: dataset.ownerships.map(Common.displayStringForUser).join(",")
            }
        }

        var summaryComponent = Clickable.create(Templates.create("DatasetSummary"), "a")
            .inMap(toViewModel)
            .state(Core.ignore)
            .emitInput()
            .outMap(toViewPage);
        var list = Common.observe(
                List.withPagination(Common.waiting, "list-sample", summaryComponent, Core.tap(function (req) {
                    // TODO エラーが欠落する
                    var stream = new Stream();
                    var ret = Api.sendDatasetsList(req);
                    ret.event.then(function (x) stream.resolve(x));
                    return { event: stream, state: ret.state };
                }, changeHash)));
        return list.render(paging);
    }
}
