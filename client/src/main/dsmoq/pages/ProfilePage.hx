package dsmoq.pages;

import dsmoq.Page;
import dsmoq.framework.types.PageContent;
import js.bootstrap.BootstrapButton;
import js.html.Element;
import js.support.ControllableStream;
import js.jsviews.JsViews;
import dsmoq.framework.View;
import js.jqhx.JqHtml;
import js.jqhx.JQuery;
import dsmoq.models.Service;

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
                                name: ""
                            }
                        },
                        email: {
                            value: Service.instance.profile.mailAddress
                        },
                        password: {
                            currentValue: "",
                            newValue: "",
                            verifyValue: ""
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
                            },
                            function (e) {
                                switch (e.name) {
                                    case ServiceErrorType.BadRequest:
                                        for (x in cast(e, ServiceError).detail) {
                                            binding.setProperty('basics.errors.${x.name}', x.message);
                                        }
                                }
                            },
                            function () {
                                BootstrapButton.reset(root.find("#basics-form-submit"));
                                root.find("#basics-form input, #basics-form textarea").removeAttr("disabled");
                            });
                        root.find("#basics-form input, #basics-form textarea").attr("disabled", true);
                    });

                    new JqHtml(container).find("#email-form-submit").on("click", function (_) {
                        Service.instance.sendEmailChangeRequests(data.email.value).then(function (_) {
                            binding.setProperty("email.value", "");
                        }, function (err) {
                            // TODO エラー処理
                        });
                    });

                    new JqHtml(container).find("#password-form-submit").on("click", function (_) {
                        Service.instance.updatePassword(data.password.currentValue, data.password.newValue).then(function (_) {
                            binding.setProperty("password.currentValue", "");
                            binding.setProperty("password.newValue", "");
                            binding.setProperty("password.verifyValue", "");
                        }, function (err) {
                            // TODO エラー処理
                        });
                    });
                }
            },
            dispose: function () {
            }
        }
    }
}