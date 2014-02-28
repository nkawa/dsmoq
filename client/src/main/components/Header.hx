package components;

import pages.Auth;
import framework.helpers.*;
using framework.helpers.Components;
import framework.Types;
import pushstate.PushState;

private typedef HeaderModel = {
    login: Bool,
    ?userId: String,
    ?userName: String
}

class Header{
    public static function create(): Component<LoginStatus, Void, Void>{
        function toModel(login:LoginStatus):HeaderModel{
            return switch(login){
                case Anonymouse: {login: false};
                case LogedIn(user): {login: true, userId: user.userId, userName: user.userName};
            };
        }
        function after(html:Html){
            (untyped html.find('.dropdown-toggle')).dropdown();     // drop down menu available by bootstrap.js

            // TODO: force change url
            var links = [
                    {selector: "[data-link-datasets]", url: "/datasets/show/"},
                    {selector: "[data-link-groups]", url: "/groups/show/"},
                    {selector: "[data-link-upload]", url: "/datasets/new/"},
                    {selector: "[data-link-profile]", url: "/profile/"}
                ];
            Lambda.iter(links, function(t){
                html.find(t.selector).on("click", function(_){ PushState.push(t.url);});
            });
            return html;
        }

        return Templates.create("header").inMap(toModel).stateMap(Core.ignore).decorate(after);
    }
}
