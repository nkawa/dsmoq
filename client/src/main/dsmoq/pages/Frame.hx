package dsmoq.pages;

import conduitbox.Engine;
import conduitbox.Frame;
import conduitbox.Navigation;
import dsmoq.models.Service;
import dsmoq.Page;
import dsmoq.View;
import hxgnd.js.JQuery;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Stream;
import hxgnd.StreamBroker;
import js.bootstrap.BootstrapButton;
import js.Browser;
import js.html.Event;
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
            location: getAuthUrl()
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
                case SignedIn, SignedOut:
                    updateProfile(Service.instance.profile);
                    navigation.update(Navigation.Reload);
                case ProfileUpdated:
                    updateProfile(Service.instance.profile);
            }
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
                            binding.setProperty("signinData.error", detail[0].message);
                    }
                },
                function () {
                    BootstrapButton.reset(JQuery._("#signin-submit"));
                    JQuery._("#signin-with-google").removeAttr("disabled");
                }
            );
        });

        header.on("click", "#signout-button", function (_) {
            Service.instance.signout();
        });

        JQuery._("#new-dataset-dialog").on("hide.bs.modal", function (_) {
            JQuery._("#new-dataset-dialog form .form-group").remove();
            JQuery._("#new-dataset-dialog form")
                .append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\"></div>");
        });

        JQuery._("#new-dataset-dialog form").on("change", "input[type='file']", function (event: Event) {
            var form = JQuery._("#new-dataset-dialog form");

            form.find("input[type='file']")
                .toArray()
                .filter(function (x) return JQuery._(x).val() == "")
                .iter(function (x) JQuery._(x).parent().remove());

            if (form.find("input[type='file']").length == 0) {
                JQuery._("#new-dataset-dialog-submit").attr("disabled", true);
            } else {
                JQuery._("#new-dataset-dialog-submit").removeAttr("disabled");
            }

            form.append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\"></div>");
        });

        JQuery._("#new-dataset-dialog-submit").on("click", function (event: Event) {
            BootstrapButton.setLoading(JQuery._("#new-dataset-dialog-submit"));
            Service.instance.createDataset(JQuery._("#new-dataset-dialog form"), JQuery._("#saveLocal").prop("checked"), JQuery._("#saveS3").prop("checked")).then(function (data) {
                untyped JQuery._("#new-dataset-dialog").modal("hide");
                JQuery._("#new-dataset-dialog form")
                    .find("input[type='file']").remove().end()
                    .append("<div class=\"form-group\"><input type=\"file\" name=\"file[]\"></div>");
				JQuery._("#saveLocal").prop("checked", false);
				JQuery._("#saveS3").prop("checked", false);
                navigation.update(Navigation.Navigate(DatasetShow(data.id)));
                Notification.show("success", "create successful");
            }, function (err) {
                Notification.show("error", "error happened");
            }, function () {
                BootstrapButton.reset(JQuery._("#new-dataset-dialog-submit"));
            });
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
            }, function (err) {
                switch (err.name) {
                    case ServiceErrorType.BadRequest:
                        for (x in cast(err, ServiceError).detail) {
                            binding.setProperty('groupForm.errors.${x.name}', x.message);
                        }
                }
                Notification.show("error", "error happened");
            }, function () {
                BootstrapButton.reset(JQuery._("#new-group-dialog-submit"));
            });
        });

        body.removeClass("loading");

        return {
            navigation: navigation.stream,
            slot: JQuery._("#main")
        }
    }

    static function getAuthUrl() {
        return "/google_oauth/signin?location=" + Engine.currentUrl.urlEncode();
    }
}