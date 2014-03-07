package pages;
import framework.Types;
import components.ConnectionPanel;

class Common{
    public static function waiting(html){
        html.text("waiting...");
    }

    public static function connectionPanel<Output>(
        name,
        component: Component<Json, Void, Output>
    ){
        return ConnectionPanel.create.bind(waiting);
    }
}
