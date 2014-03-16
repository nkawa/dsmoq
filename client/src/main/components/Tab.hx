package components;
import framework.Types;
import framework.JQuery;
import framework.helpers.*;
import framework.helpers.Components;

typedef TabPane<Output> = {
    name: String,
    title: String,
    component: Component<Dynamic, Dynamic, Output>
}

typedef TabBuilder<Output> = {
    panes: Array<TabPane<Output>>
}

class Tab{
    public static function base<Output>(): TabBuilder<Output>{
        return {panes: []};
    }

    public static function append<Output>(builder: TabBuilder<Output>, pane: TabPane<Output>): TabBuilder<Output>{
        builder.panes.push(pane);
        return {panes: builder.panes};
    }

    public static function toComponent<Input, State, Output>(builder: TabBuilder<Output>): Component<Input, State, Output>{
        function renderHeader(panes: TabPane<Output>){
            return JQuery.j('<li><a href="#${panes.name}" data-toggle="tab">${panes.title}</a></li>');
        }
        function renderEach(name, component){
            return Components.decorate(component, JQuery.wrapBy.bind('<div class="tab-pane" id="$name"></div>'));
        }
        var header = JQuery.gather(JQuery.j('<ul class="nav nav-tabs"></ul>'))(builder.panes.map(renderHeader));
        var baseComponent = Components.fromHtml(function(x){ 
            return JQuery.div().addClass("tab-panel").append(JQuery.div().addClass("tab-content"));
        });
        var tabContent = Lambda.fold(builder.panes, function(pane, acc){
            return Components.inject(pane.name, '.tab-content', acc, renderEach(pane.name, pane.component));
        }, baseComponent);

        return untyped (Components.decorateWithInput(tabContent, function(html, x){
            function tabUI(klass){
                return header.find('a[href="#$klass"]').parent();
            }
            var tabInfo = x.componentTab;
            var activeTab = tabInfo.name;
            tabUI(activeTab).addClass("active");

            var disables = (tabInfo.disables == null) ? [] : tabInfo.disables;
            Lambda.iter(disables, function(x){
                tabUI(x).addClass("disabled").find('a').prop("disabled", true);
            });

            html.find('#$activeTab').addClass("active");

            return header.add(html);
        }));
    }
}
