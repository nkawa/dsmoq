package dsmoq.models;

import js.support.ControllablePromise;
import js.support.Option;
import js.support.Promise;
import js.support.Promise;
import js.support.Stream;
import js.support.Stream;
import js.support.Unit;
import js.Cookie;
import js.Error;
import js.jqhx.JqHtml;
import js.jqhx.JQuery;
using dsmoq.framework.helper.JQueryTools;

class Service extends Stream<ServiceEvent> {
    public static var instance(default, null) = new Service();

    public var bootstrap(default, null): Promise<Unit>;
    public var profile(default, null): Profile;
    public var licenses(default, null): Array<License>;

    function new() {
        super(function (_, _, _) {
            return function () {};
        });

        profile = guest();
        licenses = [];

        bootstrap = Promise.all([
            send(Get, "/api/profile").then(function (x) profile = x).map(function (_) return Unit._),
            send(Get, "/api/licenses").then(function (x) licenses = x).map(function (_) return Unit._)
        ]).map(function (_) return Unit._);
    }

    public function signin(id: String, password: String): Promise<Unit> {
        return send(Post, "/api/signin", {id: id, password: password})
                .then(function (x) {
                    profile = x;
                    update(SignedIn);
                })
                .map(function (_) return Unit._);
    }

    public function signout(): Promise<Unit> {
        return send(Post, "/api/signout")
                .then(function (x) {
                    Cookie.remove("JSESSIONID");
                    profile = guest();
                    update(SignedOut);
                })
                .map(function (_) return Unit._);
    }

    public function updateProfile(form: JqHtml): Promise<Unit> {
        return null;
    }

    public function sendEmailChangeRequests(email: String): Void {
        // /profile/email_change_requests
    }

    public function updatePassword(currentPassword: String, newPassword: String) : Void {
        // /profile/password
    }


    public function createDataset(form: JqHtml): Promise<{id: String}> {
        return sendForm("/api/datasets", form);
    }

    public function findDatasets(?params: {?query: String, ?group: String, ?attributes: {}, ?page: UInt}): Promise<RangeSlice<{}>> {
        return send(Get, "/api/datasets", { } );
    }

    public function getDataset(id: String): Promise<Dataset> {
        return send(Get, '/api/datasets/$id');
    }

    public function deleteDeataset(id: String): Promise<Unit> {
        return send(Delete, '/api/datasets/$id');
    }


    inline function guest(): Profile {
        return {
            id: "",
            name: "",
            fullname: "",
            organization: "",
            title: "",
            image: "",
            isGuest: true
        }
    }

    function send<T>(method: RequestMethod, url: String, ?data: Dynamic): Promise<T> {
        return JQuery.ajax(url, {type: method, dataType: "json", cache: false, data: data}).toPromise()
            .bind(function (response: ApiResponse) {
                return switch (response.status) {
                    case ApiStatus.OK:
                        Promise.resolved(cast response.data);
                    case ApiStatus.BadRequest:
                        Promise.rejected(new BadRequestError("TODO error message"));
                    case ApiStatus.Unauthorized:
                        profile = guest();
                        update(SignedOut);
                        Promise.rejected(new UnauthorizedError("TODO error message"));
                    case _:
                        Promise.rejected(new Error("response error"));
                }
            });
    }

    function sendForm<T>(url: String, form: JqHtml): Promise<T> {
        var promise = new ControllablePromise();
        untyped form.ajaxSubmit({
            url: url,
            type: "post",
            dataType: "json",
            success: function (response) {
                switch (response.status) {
                    case ApiStatus.OK:
                        promise.resolve(cast response.data);
                    case ApiStatus.BadRequest:
                        promise.reject(new BadRequestError("TODO error message"));
                    case ApiStatus.Unauthorized:
                        profile = guest();
                        update(SignedOut);
                        promise.reject(new UnauthorizedError("TODO error message"));
                    case _:
                        promise.reject(new Error("response error"));
                }
            },
            error: promise.reject
        });
        return promise;
    }
}

enum ServiceEvent {
    SignedIn;
    SignedOut;
}

@:enum abstract ErrorType(String) to String {
    var BadRequest = "BadRequestError";
    var Unauthorized = "UnauthorizedError";
}

class BadRequestError extends Error {
    public function new(?message: String) {
        super(message);
        name = ErrorType.BadRequest;
    }
}

class UnauthorizedError extends Error {
    public function new(?message: String) {
        super(message);
        name = ErrorType.Unauthorized;
    }
}

@:enum
private abstract RequestMethod(String) {
    var Get = "get";
    var Post = "post";
    var Head = "head";
    var Put = "put";
    var Delete = "delete";
}

private typedef ApiResponse = {
    var status: ApiStatus;
    var data: Dynamic;
}

@:enum
private abstract ApiStatus(String) {
    var OK = "OK";
    var NotFound = "NotFound";
    var BadRequest = "BadRequest";
    var Unauthorized = "Unauthorized";
    var Error = "Error";
}