package dsmoq.components;

import dsmoq.framework.JQuery.*;
import promhx.Promise;
import dsmoq.framework.Types;
import dsmoq.components.LoadingPanel;

import dsmoq.framework.helpers.*;
using dsmoq.framework.helpers.Components;

typedef PagingRequest = {?offset: Int, ?count: Int}
typedef Paging =        {offset: Int, count: Int, total: Int}
typedef MassResult<A> = {summary: Paging, results: A}

typedef Assign<Input, State, Output> = {selector: String, component: Component<Input, State, Output>}

class Pagination{
    public static function injectInto<Input,State,Output>(
        waiting: Html -> Void,
        name: String,
        placeForPagination: String,
        placeForResult: String,
        component: Component<Input,Void,Output>,
        f: PagingRequest -> {event: Promise<MassResult<Input>>, state: Void -> State}
    ){
        function extractResult(x:MassResult<Input>){return x.results;}

        var pagination = create().outMap(Inner);
        var baseComponent = component.inMap(extractResult).outMap(Outer);
        var component: Component<MassResult<Input>, Void, NextChange<PagingRequest, Output>> =
            Components.put("summary", placeForPagination, baseComponent, pagination)
            .justView(result(), "summary", placeForResult);

        return LoadingPanel.create(waiting, name, component, f, dsmoq.framework.Effect.global().connectionError);
    }
    public static function end(paging:Paging){
        var n = paging.offset + paging.count;
        return (paging.total < n) ? paging.total : n;
    }

    public static function result():Component<Paging, Void, Void>{
        return Components.fromHtml(function(paging:Paging){
            return j('<strong>Found ${paging.total} records (${paging.offset + 1} - ${end(paging)})</strong>');
        });
    }

    public static function create(){
        function nextPaging(current: Paging){
            return function(nextPage: Int): PagingRequest{
                var next = (nextPage - 1) * current.count + 1;
                return {offset: next, count: current.count};
            };
        }
        function render(paging: Paging){
            var p = new Promise();
            var currentPage = Std.int(paging.offset / paging.count) + 1;
            var totalPage = Std.int((paging.total - 1) / paging.count) + 1;
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

    public inline static function top(){return {offset: 0, count: 20}; }
}
