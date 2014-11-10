package dsmoq.pages;

import conduitbox.Navigation;
import dsmoq.models.Service;
import dsmoq.Page;
import dsmoq.views.ViewTools;
import hxgnd.js.Html;
import hxgnd.js.jsviews.JsViews;
import hxgnd.Promise;
import hxgnd.PromiseBroker;
import hxgnd.Unit;
import js.bootstrap.BootstrapButton;

class ProfilePage {
    public static function render(root: Html, onClose: Promise<Unit>): Promise<Navigation<Page>> {
        var navigation = new PromiseBroker();
        if (Service.instance.profile.isGuest) {
            root.html("unauthorized");
        } else {
            var data = {
                basics: {
                    name: Service.instance.profile.name,
                    fullname: Service.instance.profile.fullname,
                    organization: Service.instance.profile.organization,
                    title: Service.instance.profile.title,
                    description: Service.instance.profile.description,
                    errors: {
                        name: "",
                        fullname: "",
                        organization: "",
                        title: "",
                        description: "",
                        image: "",
                    }
                },
                icon: {
                    image: Service.instance.profile.image,
                    errors: { icon: "" }
                },
                email: {
                    email: Service.instance.profile.mailAddress,
                    errors: { value: "" }
                },
                password: {
                    currentValue: "",
                    newValue: "",
                    verifyValue: "",
                    errors: {
                        currentValue: "",
                        newValue: "",
                        verifyValue: ""
                    }
                }
            };

            var binding = JsViews.observable(data);
            View.getTemplate("profile/edit").link(root, data);

            Service.instance.then(function (e) {
                switch (e) {
                    case ServiceEvent.ProfileUpdated:
                        var profile = Service.instance.profile;
                        binding.setProperty({
                            "basics.name": profile.name,
                            "basics.fullname": profile.fullname,
                            "basics.organization": profile.organization,
                            "basics.title": profile.title,
                            "basics.description": profile.description,
                            "icon.image": profile.image,
                            "email.email": profile.mailAddress
                        });
                    case _:
                }
            });

            root.find("#basics-form-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#basics-form-submit"));
                Service.instance.updateProfile(data.basics.name, data.basics.fullname,
                        data.basics.organization, data.basics.title, data.basics.description).then(
                    function (x) {
                        binding.setProperty("basics.errors.name", "");
                        binding.setProperty("basics.errors.fullname", "");
                        binding.setProperty("basics.errors.organization", "");
                        binding.setProperty("basics.errors.title", "");
                        binding.setProperty("basics.errors.description", "");
                        binding.setProperty("basics.errors.image", "");
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        switch (e.name) {
                            case ServiceErrorType.BadRequest:
                                binding.setProperty("basics.errors.name", "");
                                binding.setProperty("basics.errors.fullname", "");
                                binding.setProperty("basics.errors.organization", "");
                                binding.setProperty("basics.errors.title", "");
                                binding.setProperty("basics.errors.description", "");
                                binding.setProperty("basics.errors.image", "");
                                for (x in cast(e, ServiceError).detail) {
                                    binding.setProperty('basics.errors.${x.name}', x.message);
                                }
                        }
                        Notification.show("error", "error happened");
                    },
                    function () {
                        BootstrapButton.reset(root.find("#basics-form-submit"));
                        root.find("#basics-form input, #basics-form textarea").removeAttr("disabled");
                    });
                root.find("#basics-form input, #basics-form textarea").attr("disabled", true);
            });

            root.find("#image-form-submit").on("click", function (_) {
                ViewTools.showLoading("body");
                Service.instance.updateImage(root.find("#image-form")).then(function (_) {
                    binding.setProperty("icon.errors.image", "");
                    ViewTools.hideLoading("body");
                    Notification.show("success", "save successful");
                }, function (e) {
                    switch (e.name) {
                        case ServiceErrorType.BadRequest:
                            for (x in cast(e, ServiceError).detail) {
                                binding.setProperty('email.errors.${x.name}', x.message);
                            }
                    }
                    ViewTools.hideLoading("body");
                    Notification.show("error", "error happened");
                });
            });

            root.find("#email-form-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#email-form-submit"));
                Service.instance.sendEmailChangeRequests(data.email.email).then(
                    function (_) {
                        binding.setProperty("email.errors.email", "");
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        switch (e.name) {
                            case ServiceErrorType.BadRequest:
                                for (x in cast(e, ServiceError).detail) {
                                    binding.setProperty('email.errors.${x.name}', x.message);
                                }
                        }
                        Notification.show("error", "error happened");
                    }, function () {
                        BootstrapButton.reset(root.find("#email-form-submit"));
                    }
                );
            });

            root.find("#password-form-submit").on("click", function (_) {
                if (data.password.newValue != data.password.verifyValue) {
                    binding.setProperty("password.errors.verifyValue", "invalid verify password");
                    return;
                }

                BootstrapButton.setLoading(root.find("#password-form-submit"));
                Service.instance.updatePassword(data.password.currentValue, data.password.newValue).then(
                    function (_) {
                        binding.setProperty("password.currentValue", "");
                        binding.setProperty("password.newValue", "");
                        binding.setProperty("password.verifyValue", "");
                        binding.setProperty("password.errors.currentValue", "");
                        binding.setProperty("password.errors.newValue", "");
                        binding.setProperty("password.errors.verifyValue", "");
                        Notification.show("success", "save successful");
                    },
                    function (e) {
                        switch (e.name) {
                            case ServiceErrorType.BadRequest:
                                binding.setProperty("password.errors.currentValue", "");
                                binding.setProperty("password.errors.newValue", "");
                                binding.setProperty("password.errors.verifyValue", "");
                                for (x in cast(e, ServiceError).detail) {
                                    var name = switch (x.name) {
                                        case "new_password": "newValue";
                                        case "current_password": "currentValue";
                                        case _: x.name;
                                    }
                                    binding.setProperty('password.errors.${name}', x.message);
                                }
                        }
                        Notification.show("error", "error happened");
                    },
                    function () {
                        BootstrapButton.reset(root.find("#password-form-submit"));
                    });
            });
        }

        return navigation.promise;
    }
}