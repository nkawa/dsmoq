package dsmoq.components;

import dsmoq.pages.Models;
import dsmoq.framework.helpers.*;
import promhx.Stream;
using dsmoq.framework.helpers.Components;
import dsmoq.framework.JQuery;
import dsmoq.framework.Effect;
import js.Browser.document in doc;
import dsmoq.pages.Definitions;
import dsmoq.framework.types.ComponentFactory;
import dsmoq.framework.types.PageEvent;
import dsmoq.framework.types.Html;

private typedef HeaderModel = {
    login: Bool,
    ?userId: String,
    ?userName: String
}

class Header {
    public static function create(): ComponentFactory<LoginStatus, Void, PageEvent<Page>> {
        var stream = new Stream();

        function toModel(login: LoginStatus): HeaderModel {
            return switch (login) {
                case Anonymouse: {login: false};
                case LogedIn(user): {login: true, userId: user.userId, userName: user.userName};
            };
        }

        function after(html: Html) {
            (untyped html.find('.dropdown-toggle')).dropdown();     // drop down menu available by bootstrap.js

            // TODO: force change url
            var links = [
                    {selector: "[data-link-home]", page: Page.DashBoard},
                    {selector: "[data-link-datasets]", page: DatasetList(None) },
                    {selector: "[data-link-groups]", page: GroupList },
                    {selector: "[data-link-upload]", page: DatasetNew },
                    {selector: "[data-link-profile]", page: Profile }
                ];
            Lambda.iter(links, function(t){
                html.find(t.selector).on("click", function(_) stream.resolve(PageEvent.Navigate(t.page)));
            });

            clickToSubmit(html, "[data-submit-login]",  "[data-link-login]",  Settings.api.login, function(_) { html.find('[data-message-login]').text(Messages.loginFailure); } );
            clickToSubmit(html, "[data-submit-logout]", "[data-link-logout]", Settings.api.logout);
			clickToRedirect(html, "[data-submit-google-login]",  "[data-link-google-login]", "oauth/signin_google");

            return html;
        }

        var cmp = Templates.create("header").inMap(toModel).stateMap(Core.ignore).decorate(after);

        return {
            render: function (status: LoginStatus) {
                var rendered = cmp.render(status);
                return { html: rendered.html, event: stream, state: function () {} }
            }
        };
    }

    private static function clickToSubmit(html: Html, formSelector, clickSelector, url, onError = null) {
        JQuery.findAll(html, clickSelector).on("click", function(_) {
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
			var location = StringTools.urlEncode(doc.location.pathname + doc.location.search + doc.location.hash);
			doc.location.href = url + "?location=" + location;
        });
	}
}
