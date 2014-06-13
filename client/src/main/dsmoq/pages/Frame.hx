package dsmoq.pages;

import dsmoq.framework.ApplicationContext;
import dsmoq.framework.helper.PageHelper;
import dsmoq.framework.LocationTools;
import dsmoq.framework.types.PageFrame;
import dsmoq.framework.types.PageNavigation;
import dsmoq.framework.View;
import dsmoq.models.Service;
import dsmoq.Page;
import js.bootstrap.BootstrapButton;
import js.Browser;
import js.html.Event;
import js.jqhx.JQuery;
import js.jsviews.JsViews;
import js.support.ControllableStream;

using StringTools;
using js.support.ArrayTools;

class Frame {
    public static function create(context: ApplicationContext): PageFrame<Page> {
        var body = JQuery.wrap(Browser.document.body);
        var navigation = new ControllableStream();

        function url(location) {
            return "/oauth/signin_google?location=" + LocationTools.toUrl(location).urlEncode();
        }

        var data = {
            profile: Service.instance.profile,
            signinData: {
                id: "",
                password: "",
                error: ""
            },
            location: url(LocationTools.currentLocation())
        };

        var binding = JsViews.objectObservable(data);

        var header = JQuery.find("#header");
        View.link("header", header, data);

        context.location.then(function (location) {
            binding.setProperty("location", url(location));
        });

        function updateProfile(profile) {
            binding.setProperty("profile.id", profile.id);
            binding.setProperty("profile.name", profile.name);
            binding.setProperty("profile.fullname", profile.fullname);
            binding.setProperty("profile.organization", profile.organization);
            binding.setProperty("profile.title", profile.title);
            binding.setProperty("profile.description", profile.description);
            binding.setProperty("profile.image", profile.image);
            binding.setProperty("profile.mailAddress", profile.mailAddress);
            binding.setProperty("profile.isGuest", profile.isGuest);
            binding.setProperty("profile.isDeleted", profile.isDeleted);
        }

        Service.instance.then(function (e) {
            switch (e) {
                case SignedIn, SignedOut:
                    updateProfile(Service.instance.profile);
                    navigation.update(PageNavigation.Reload);
                case ProfileUpdated:
                    updateProfile(Service.instance.profile);
            }
        });

        header.on("submit", "#signin-form", function (event: Event) {
            event.preventDefault();

            BootstrapButton.setLoading(JQuery.find("#signin-submit"));
            JQuery.find("#signin-with-google").attr("disabled", true);
            Service.instance.signin(data.signinData.id, data.signinData.password).then(
                function (_) {
                    binding.setProperty("signinData.id", "");
                    binding.setProperty("signinData.password", "");
                    binding.setProperty("signinData.error", "");
                },
                function (e) {
                    switch (e.name) {
                        case ServiceErrorType.BadRequest:
                            var detail = cast(e, ServiceError).detail;
                            binding.setProperty("signinData.error", detail[0].message);
                    }
                },
                function () {
                    BootstrapButton.reset(JQuery.find("#signin-submit"));
                    JQuery.find("#signin-with-google").removeAttr("disabled");
                }
            );
        });

        header.on("click", "#signout-button", function (_) {
            Service.instance.signout();
        });

        JQuery.find("#new-dataset-dialog").on("hide.bs.modal", function (_) {
            JQuery.find("#new-dataset-dialog form .form-group").remove();
            JQuery.find("#new-dataset-dialog form")
                .append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\"></div>");
        });

        JQuery.find("#new-dataset-dialog form").on("change", "input[type='file']", function (event: Event) {
            JQuery.find("#new-dataset-dialog form input[type='file']")
                .toArray()
                .filter(function (x) return JQuery.wrap(x).val() == "")
                .iter(function (x) JQuery.wrap(x).parent().remove());
            JQuery.find("#new-dataset-dialog form")
                .append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\"></div>");
        });

        JQuery.find("#new-dataset-dialog-submit").on("click", function (event: Event) {
            // TODO ui block
            Service.instance.createDataset(JQuery.find("#new-dataset-dialog form")).then(function (data) {
                untyped JQuery.find("#new-dataset-dialog").modal("hide");
                navigation.update(PageNavigation.Navigate(DatasetShow(data.id)));
            });
        });

        JQuery.find("#new-group-dialog-submit").on("click", function (event: Event) {
            // TODO ui block
            var name = JQuery.find("#new-group-dialog input[name=name]").val();
            Service.instance.createGroup(name).then(function (data) {
                untyped JQuery.find("#new-group-dialog").modal("hide");
                navigation.update(PageNavigation.Navigate(GroupShow(data.id)));
                // TODO form clear
            });
        });

        body.removeClass("loading");

        return PageHelper.toFrame({
            html: cast JQuery.find("#main")[0],
            navigation: navigation
        });
    }
}