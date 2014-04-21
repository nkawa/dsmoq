package dsmoq.components;

import promhx.Stream;
import dsmoq.framework.JQuery;
import dsmoq.pages.Models;
import dsmoq.framework.helpers.*;
using dsmoq.framework.helpers.Components;

import dsmoq.components.Pagination;
import dsmoq.framework.types.Component;
import dsmoq.framework.types.PlaceHolder;
import dsmoq.framework.types.Html;

class List{
    static inline var CLASS_TO_PUT_PLACEHOLDER = "component-placeholder";
    static inline var CLASS_TO_PUT_SEARCH_RESULT = "component-search-result";

    public static function create<Input, State, Output>(component: Component<Input, State, Output>): Component<Array<Input>, Void, Output>{
        return Components.list(component, JQuery.join('<li class="dataset-item"></li>'))
            .state(Core.ignore)
            .decorate(JQuery.wrapBy.bind('<ul></ul>'));
    }

    public static function withPagination<Input, State, Output>(
        waiting: Html -> Void,
        name: String,
        component: Component<Input,Void,Output>,
        f: PagingInfo -> {event: Stream<MassResult<Array<Input>>>, state: Void -> State}
    ):PlaceHolder<PagingInfo, State, Output>{
        var list = create(component).decorate(function(html){
            return JQuery.div().addClass(CLASS_TO_PUT_SEARCH_RESULT)
                .add(html)
                .add(JQuery.div().addClass(CLASS_TO_PUT_PLACEHOLDER));
        });
        return Pagination.injectInto(waiting, name, '.$CLASS_TO_PUT_PLACEHOLDER', '.$CLASS_TO_PUT_SEARCH_RESULT', list, f);
    }
}
