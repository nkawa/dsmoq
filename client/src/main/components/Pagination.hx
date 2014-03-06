package components;

import framework.JQuery.*;
import promhx.Promise;
import framework.Types;
import components.LoadingPanel;

import framework.helpers.*;
using framework.helpers.Components;

typedef PagingRequest = {num: Int, numPerPage: Int}
typedef Paging =        {num: Int, numPerPage: Int, total: Int}
typedef MassResult<A> = {paging: Paging, result: A}

typedef Selector = String

class Pagination{
    public static function injectInto<Input,State,Output>(
        waiting: Html -> Void,
        name: String,
        placeForPagination: String,
        component: Component<Input,Void,Output>,
        f: PagingRequest -> {event: Promise<MassResult<Input>>, state: Void -> State}
    ){
        function extractResult(x:MassResult<Input>){return x.result;}

        var pagination = create().outMap(Inner);
        var baseComponent = component.inMap(extractResult).outMap(Outer);
        var component: Component<MassResult<Input>, Void, NextChange<PagingRequest, Output>> =
            Components.put("paging", placeForPagination)(baseComponent, pagination);
        return LoadingPanel.create(waiting, name, component, f);
    }

    public static function create(){
        function nextPaging(current: Paging){
            return function(nextPage: Int): PagingRequest{
                var next = (nextPage - 1) * current.numPerPage + 1;
                return {num: next, numPerPage: current.numPerPage};
            };
        }
        function render(paging: Paging){
            var p = new Promise();
            var currentPage = Std.int(paging.num / paging.numPerPage) + 1;
            var totalPage = Std.int((paging.total - 1) / paging.numPerPage) + 1;
            var calculateNext = nextPaging(paging);
            function each(i){
                return if(i == currentPage)
                    j('<li class="active"/>').append(
                            j('<a>${Std.string(i)}<span class="sr-only">(current)</span></a>'));
                else
                    j('<li/>').append(
                            j('<a>${Std.string(i)}</a>').on("click", function(_){ p.resolve(calculateNext(i));}));
            }
            function side(mark, enable, next){
                var el = j('<a>$mark</a>');
                if(enable){
                    el.addClass("disabled").on("click", function(_){p.resolve(calculateNext(next));});
                }
                return j('<li>').append(el);
            }
            var body = j('<ul class="pagination">')
                .append(side("&laquo", currentPage > 1, currentPage - 1))
                .append(Lambda.fold([for (i in 1...(totalPage+1)) i], function(i, acc:Html){return acc.add(each(i));}, j('')))
                .append(side("&raquo", currentPage < totalPage, currentPage + 1));
            return { html: body, event: p, state: function(){return paging;}};
        }
        return Components.toComponent(render);
    }
}
