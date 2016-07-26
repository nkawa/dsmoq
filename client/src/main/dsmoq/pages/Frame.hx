package dsmoq.pages;

import conduitbox.Engine;
import conduitbox.Frame;
import conduitbox.Navigation;
import dsmoq.models.Service;
import dsmoq.Page;
import dsmoq.View;
import hxgnd.js.JQuery;
import hxgnd.js.jsviews.JsViews;
import hxgnd.PromiseBroker;
import hxgnd.Stream;
import hxgnd.StreamBroker;
import js.bootstrap.BootstrapButton;
import js.Browser;
import js.html.Event;
import js.html.InputElement;
import hxgnd.js.JsTools;

using StringTools;
using hxgnd.ArrayTools;

class Frame {
    public static function create(onNavigated: Stream<Page>) {
        var body = JQuery._(Browser.document.body);
        var navigation = new StreamBroker();

        var data = {
            profile: Service.instance.profile,
            signinData: {
                id: "",
                password: "",
                error: ""
            },
            groupForm: {
                name: "",
                errors: { name: "" }
            },
            location: getAuthUrl(),
            statistics: Async.Pending
        };

        var binding = JsViews.observable(data);

        var header = JQuery._("#header");
        View.link("header", header, data);

        onNavigated.then(function (_) {
            binding.setProperty("location", getAuthUrl());
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
                case SignedIn:
                    js.Browser.location.href = "/dashboard";
                    //updateProfile(Service.instance.profile);
                    //navigation.update(Navigation.Navigate(Page.Dashboard));
                case SignedOut:
                    js.Browser.location.href = "/";
                    //navigation.update(Navigation.Navigate(Page.Top));
                    //updateProfile(Service.instance.profile);
                case ProfileUpdated:
                    //updateProfile(Service.instance.profile);
            }
        });
        
        Service.instance.getStatistics({ }).then(function(x) {
            binding.setProperty("statistics", Async.Completed(x));
        });
        
        header.on("submit", "#signin-form", function (event: Event) {
            event.preventDefault();

            BootstrapButton.setLoading(JQuery._("#signin-submit"));
            JQuery._("#signin-with-google").attr("disabled", true);
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
                            Notification.show("error", detail[0].message);
                    }
                },
                function () {
                    BootstrapButton.reset(JQuery._("#signin-submit"));
                    JQuery._("#signin-with-google").removeAttr("disabled");
                }
            );
        });
        
        header.on("click", "#submitForm", function(_) { 
            JQuery._("#signin-form").submit();
        } );

        header.on("click", "#signout-button", function (_) {
            Service.instance.signout();
        });

        JQuery._("#new-dataset-dialog").on("hide", function (_) {
            JQuery._("#files-row .form-group").remove();
            JQuery._("#files-row").append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\" multiple=\"multiple\"></div>");
            JQuery._("#dataset-name").val("");
            JQuery._("#saveLocal").prop("checked", true);
            JQuery._("#saveS3").prop("checked", false);
            JQuery._("#new-dataset-dialog-submit").prop("disabled", true);
        });

        JQuery._("#dataset-name").on("change", function(event: Event) { 
            var value = JQuery._(event.target).val();
            JQuery._("#new-dataset-dialog-submit").prop("disabled", value == "");
        });
        
        JQuery._("#new-dataset-dialog form").on("change", "input[type='file']", function (event: Event) {
            var input = cast(event.target, InputElement);
            
            if (JQuery._("#dataset-name").val() == "") {
                JQuery._("#dataset-name").val(input.files[0].name);
            }
            
            var form = JQuery._("#new-dataset-dialog form");

            form.find("input[type='file']")
                .toArray()
                .filter(function (x) return JQuery._(x).val() == "")
                .iter(function (x) JQuery._(x).parent().remove());
            JQuery._("#new-dataset-dialog-submit").prop("disabled", false);
            JQuery._("#files-row").append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\" multiple=\"multiple\"></div>");
        });

        JQuery._("#new-dataset-dialog-submit").on("click", function (event: Event) {
            BootstrapButton.setLoading(JQuery._("#new-dataset-dialog-submit"));
            Service.instance.createDataset(JQuery._("#new-dataset-dialog form"), JQuery._("#saveLocal").prop("checked"), JQuery._("#saveS3").prop("checked")).then(function (data) {
                untyped JQuery._("#new-dataset-dialog").modal("hide");
                JQuery._("#files-row .form-group").remove();
                JQuery._("#files-row").append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\" multiple=\"multiple\"></div>");
                JQuery._("#dataset-name").val("");
                JQuery._("#saveLocal").prop("checked", true);
                JQuery._("#saveS3").prop("checked", false);
                JQuery._("#new-dataset-dialog-submit").prop("disabled", true);
                navigation.update(Navigation.Navigate(DatasetShow(data.id)));
                Notification.show("success", "create successful");
            }, function (err) {
            }, function () {
                BootstrapButton.reset(JQuery._("#new-dataset-dialog-submit"));
                JQuery._("#new-dataset-dialog").find(":input").prop("disabled", false);
            });
            JQuery._("#new-dataset-dialog").find(":input").prop("disabled", true);
        });

        function toggleNewGroupSubmit() {
            if (JQuery._("#new-group-dialog input[name='name']").val().length <= 0) {
                JQuery._("#new-group-dialog-submit").attr("disabled", true);
            } else {
                JQuery._("#new-group-dialog-submit").removeAttr("disabled");
            }
        }

        JQuery._("#new-group-dialog input[name='name']")
            .on("paste", function (event: Event) {
                JsTools.setImmediate(toggleNewGroupSubmit);
            })
            .on("cut", function (event: Event) {
                JsTools.setImmediate(toggleNewGroupSubmit);
            })
            .on("change", function (event: Event) {
                toggleNewGroupSubmit();
            })
            .on("keydown", function (event: Event) {
                JsTools.setImmediate(toggleNewGroupSubmit);
            });

        JQuery._("#new-group-dialog-submit").on("click", function (event: Event) {
            BootstrapButton.setLoading(JQuery._("#new-group-dialog-submit"));

            Service.instance.createGroup(data.groupForm.name).then(function (data) {
                untyped JQuery._("#new-group-dialog").modal("hide");
                binding.setProperty('groupForm.name', "");
                binding.setProperty('groupForm.errors.name', "");
                JQuery._("#new-group-dialog-submit").attr("disabled", true);
                Notification.show("success", "create successful");
                navigation.update(Navigation.Navigate(GroupShow(data.id)));
            }, function (err: Dynamic) {
                switch (err.responseJSON.status) {
                    case ApiStatus.IllegalArgument:
                        binding.setProperty('groupForm.errors.name', err.responseJSON.data.value);
                }
            }, function () {
                BootstrapButton.reset(JQuery._("#new-group-dialog-submit"));
            });
        });
        
        untyped __js__('$( "a[rel*=leanModal]").leanModal({ top: 50, overlay : 0.5, closeButton: ".modal_close"});');
        
        body.removeClass("loading");

        return {
            navigation: navigation.stream,
            slot: JQuery._("#main")
        }
    }

    static function getAuthUrl() {
        return "/google_oauth/signin?location=/dashboard";
    }
}
