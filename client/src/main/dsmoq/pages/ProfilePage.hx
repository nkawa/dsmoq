package dsmoq.pages;

import dsmoq.framework.types.PageContent;
import dsmoq.framework.View;
import dsmoq.models.Service;
import dsmoq.Page;
import js.bootstrap.BootstrapButton;
import js.html.Element;
import js.jqhx.JqHtml;
import js.jsviews.JsViews;
import js.support.ControllableStream;

class ProfilePage {
    public static function create(): PageContent<Page> {
        return {
            navigation: new ControllableStream(),
            invalidate: function (container: Element) {
                var root = new JqHtml(container);

                if (Service.instance.profile.isGuest) {
                    container.innerHTML = "unauthorized";
                } else {
                    var data = {
                        basics: {
                            name: Service.instance.profile.name,
                            fullname: Service.instance.profile.fullname,
                            organization: Service.instance.profile.organization,
                            title: Service.instance.profile.title,
                            description: Service.instance.profile.description,
                            image: Service.instance.profile.image,
                            errors: {
                                name: "",
                                fullname: "",
                                organization: "",
                                title: "",
                                description: "",
                                image: "",
                            }
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

                    var binding = JsViews.objectObservable(data);
                    View.getTemplate("profile/edit").link(container, data);

                    root.find("#basics-form-submit").on("click", function (_) {
                        BootstrapButton.setLoading(root.find("#basics-form-submit"));
                        Service.instance.updateProfile(new JqHtml(container).find("#basics-form")).then(
                            function (x) {
                                binding.setProperty("basics.name", x.name);
                                binding.setProperty("basics.fullname", x.fullname);
                                binding.setProperty("basics.organization", x.organization);
                                binding.setProperty("basics.title", x.title);
                                binding.setProperty("basics.description", x.description);
                                binding.setProperty("basics.image", x.image);
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
                                        for (x in cast(e, ServiceError).detail) {
                                            var name = switch (x.name) {
                                                case "new_password": "newValue";
                                                case "current_password": "currentValue";
                                                case _: x.name;
                                            }
                                            binding.setProperty('password.errors.${name}', x.message);
                                        }
                                }
                                trace(data);
                                Notification.show("error", "error happened");
                            },
                            function () {
                                BootstrapButton.reset(root.find("#password-form-submit"));
                            });
                    });
                }
            },
            dispose: function () {
            }
        }
    }
}