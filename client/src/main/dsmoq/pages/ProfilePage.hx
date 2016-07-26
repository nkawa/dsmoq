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
            
            var isGoogleUser = Service.instance.profile.isGoogleUser;            
            if (isGoogleUser) {
                root.find(".disable-form-target").attr("disabled", true);
            }

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
                    function (e: Dynamic) {
                        switch (e.responseJSON.status) {
                            case ApiStatus.IllegalArgument:
                                binding.setProperty("basics.errors.name", "");
                                binding.setProperty("basics.errors.fullname", "");
                                binding.setProperty("basics.errors.organization", "");
                                binding.setProperty("basics.errors.title", "");
                                binding.setProperty("basics.errors.description", "");
                                binding.setProperty("basics.errors.image", "");
                                var name = StringTools.replace(e.responseJSON.data.key, "d.", "");
                                binding.setProperty('basics.errors.${name}', StringTools.replace(e.responseJSON.data.value, "d.", ""));
                            case ApiStatus.BadRequest:
                                binding.setProperty("basics.errors.name", "");
                                binding.setProperty('basics.errors.name', StringTools.replace(e.responseJSON.data, "d.", ""));
                        }
                    },
                    function () {
                        BootstrapButton.reset(root.find("#basics-form-submit"));
                        root.find("#basics-form input, #basics-form textarea").removeAttr("disabled");
                        if (isGoogleUser) {
                            root.find(".disable-form-target").attr("disabled", true);
                        }
                    });
                root.find("#basics-form input, #basics-form textarea").attr("disabled", true);
            });

            root.find("#image-form-submit").on("click", function (_) {
                ViewTools.showLoading("body");
                Service.instance.updateImage(root.find("#image-form")).then(function (_) {
                    binding.setProperty("icon.errors.image", "");
                    ViewTools.hideLoading("body");
                    Notification.show("success", "save successful");
                }, function (e: Dynamic) {
                    switch (e.responseJSON.status) {
                        case ApiStatus.IllegalArgument:
                            binding.setProperty("icon.errors.image", "");
                            binding.setProperty('email.errors.image', StringTools.replace(e.responseJSON.data.value, "d.", ""));
                    }
                    ViewTools.hideLoading("body");
                });
            });

            root.find("#email-form-submit").on("click", function (_) {
                BootstrapButton.setLoading(root.find("#email-form-submit"));
                Service.instance.sendEmailChangeRequests(data.email.email).then(
                    function (_) {
                        binding.setProperty("email.errors.email", "");
                        Notification.show("success", "save successful");
                    },
                    function (e: Dynamic) {
                        switch (e.responseJSON.status) {
                            case ApiStatus.IllegalArgument:
                                binding.setProperty("email.errors.email", "");
                                binding.setProperty('email.errors.email', StringTools.replace(e.responseJSON.data.value, "d.", ""));
                        }
                    }, function () {
                        BootstrapButton.reset(root.find("#email-form-submit"));
                        if (isGoogleUser) {
                            root.find(".disable-form-target").attr("disabled", true);
                        }
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
                    function (e: Dynamic) {
                        switch (e.responseJSON.status) {
                            case ApiStatus.IllegalArgument:
                                binding.setProperty("password.errors.currentValue", "");
                                binding.setProperty("password.errors.newValue", "");
                                var name = switch (e.responseJSON.data.key) {
                                    case "d.currentPassword": "newValue";
                                    case "d.newPassword": "currentValue";
                                    case _: e.responseJSON.data.key;
                                };
                                binding.setProperty('password.errors.${name}', StringTools.replace(e.responseJSON.data.value, "d.", ""));
                            case ApiStatus.BadRequest:
                                binding.setProperty("password.errors.verifyValue", "");
                                binding.setProperty('password.errors.verifyValue', StringTools.replace(e.responseJSON.data.value, "d.", ""));
                        }
                    },
                    function () {
                        BootstrapButton.reset(root.find("#password-form-submit"));
                        if (isGoogleUser) {
                            root.find(".disable-form-target").attr("disabled", true);
                        }
                    });
            });
        }

        return navigation.promise;
    }
}
