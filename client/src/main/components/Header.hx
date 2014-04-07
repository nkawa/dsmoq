package components;

import pages.Models;
import framework.helpers.*;
using framework.helpers.Components;
import framework.Types;
import framework.JQuery;
import framework.Effect;
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
                    {selector: "[data-link-profile]", url: "/profile/" }
                ];
            Lambda.iter(links, function(t){
                html.find(t.selector).on("click", function(_){ Effect.global().changeUrl(Address.url(t.url));});
            });

            clickToSubmit(html, "[data-submit-login]",  "[data-link-login]",  Settings.api.login, function(_) { html.find('[data-message-login]').text(Messages.loginFailure); } );			
            clickToSubmit(html, "[data-submit-logout]", "[data-link-logout]", Settings.api.logout);
			clickToRedirect(html, "[data-submit-google-login]",  "[data-link-google-login]", "/api/signin_google");

            return html;
        }

        return Templates.create("header").inMap(toModel).stateMap(Core.ignore).decorate(after);
    }

    private static function clickToSubmit(html: Html, formSelector, clickSelector, url, onError = null){
        JQuery.findAll(html, clickSelector).on("click", function(_){
            var promise = Connection.ajaxSubmit(JQuery.findAll(html, formSelector), url);
            promise.then(function(response){
                if(response != null && response.status == "OK"){
                   doc.location.reload();
                }else{
                    if(onError != null) onError(response);
                }
            });
        });
    }
	
	private static function clickToRedirect(html: Html, formSelector, clickSelector, url) {
		JQuery.findAll(html, clickSelector).on("click", function(_) {
			doc.location.href = url + "?location=" + doc.location.pathname;
        });
	}
}
