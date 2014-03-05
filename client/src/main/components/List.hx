package components;
import promhx.Promise;
import framework.Types;
import framework.JQuery;
import framework.helpers.*;
using framework.helpers.Components;

import components.Pagination;

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
        f: PagingRequest -> {event: Promise<MassResult<Array<Input>>>, state: Void -> State}
    ):PlaceHolder<PagingRequest, State, Output>{
        var list = create(component).decorate(function(html){
            return JQuery.div().addClass(CLASS_TO_PUT_SEARCH_RESULT)
                .add(html)
                .add(JQuery.div().addClass(CLASS_TO_PUT_PLACEHOLDER));
        });
        return Pagination.injectInto(waiting, name, '.$CLASS_TO_PUT_PLACEHOLDER', '.$CLASS_TO_PUT_SEARCH_RESULT', list, f);
    }
}
