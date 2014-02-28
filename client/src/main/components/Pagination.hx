package components;

import framework.JQuery.*;
import promhx.Promise;
import framework.Types;
import components.LoadingPanel;

import framework.helpers.*;
using framework.helpers.Components;

typedef Paging = {num: Int, total: Int, numPerPage: Int}

class Pagination{
    public static function put<Input,State,Output>(
        name: String,
        component: Component<Input,State,Output>,
        fromPaging: Paging -> Promise<Input>
    ){
        var pagination = create();
        function dataAndPage(paging){
            return fromPaging(paging).then(function(a){
                return {data: a, paging: paging};
            });
        }
        function render(input){
            var promise = new Promise();
            var renderedComponent = component.render(input.data);
            var renderedPagination = pagination.render(input.paging);
            renderedComponent.event.then(function(c){promise.resolve(Outer(c));});
            renderedPagination.event.then(function(p){promise.resolve(Inner(p));});
            var body = div().append(renderedComponent.html).append(renderedPagination.html);
            return {html: body, state: renderedComponent.state, event:promise};
        }
        var foldable: Foldable<Paging, State, Output> = 
            LoadingPanel.create('$name-panel', Components.toComponent(render))
            .inMap(dataAndPage);
        return PlaceHolders.create(name, foldable);
    }

    public static function create(){
        function nextPaging(current: Paging){
            return function(nextPage: Int): Paging{
                var next = (nextPage - 1) * current.numPerPage + 1;
                return {num: next, total: current.total, numPerPage: current.numPerPage};
            };
        }
        function render(paging: Paging){
            var p = new Promise();
            var currentPage = Std.int(paging.num / paging.numPerPage) + 1;
            var totalPage = Std.int(paging.total / paging.numPerPage) + 1;
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
                .append(Lambda.fold([for (i in 1...totalPage) i], function(i, acc:Html){return acc.add(each(i));}, j('')))
                .append(side("&raquo", currentPage < totalPage, currentPage + 1));
            return { html: body, event: p, state: function(){return paging;}};
        }
        return Components.toComponent(render);
    }
}
