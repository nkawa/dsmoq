package components;

import pages.Auth;
import framework.helpers.*;
using framework.helpers.Components;
import framework.Types;
import framework.JQuery;
import pushstate.PushState;
import js.Browser.document in doc;

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
                    {selector: "[data-link-home]", url: "/"},
                    {selector: "[data-link-datasets]", url: "/datasets/list/"},
                    {selector: "[data-link-groups]", url: "/groups/list/"},
                    {selector: "[data-link-upload]", url: "/datasets/new/"},
                    {selector: "[data-link-profile]", url: "/profile/"}
                ];
            Lambda.iter(links, function(t){
                html.find(t.selector).on("click", function(_){ PushState.push(t.url);});
            });

            clickToSubmit(html, "[data-submit-login]",  "[data-link-login]",  Settings.api.login);
            clickToSubmit(html, "[data-submit-logout]", "[data-link-logout]", Settings.api.logout);

            return html;
        }

        return Templates.create("header").inMap(toModel).stateMap(Core.ignore).decorate(after);
    }

    private static function clickToSubmit(html: Html, formSelector, clickSelector, url){
        JQuery.findAll(html, clickSelector).on("click", function(_){
            var jqXHR = (untyped JQuery.findAll(html, formSelector)).ajaxSubmit({url: url, dataType:"JSON"}).data('jqxhr');
            // TODO: Error handling
            jqXHR.done(function(_){
                doc.location.reload();
            });
        });
    }
}
