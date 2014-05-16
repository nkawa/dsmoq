package dsmoq.models;

import dsmoq.framework.types.Option;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.Stream;
import dsmoq.framework.types.Stream;
import dsmoq.framework.types.Unit;
import js.Error;
import js.jqhx.JqHtml;
import js.jqhx.JQuery;
using dsmoq.framework.helper.PromiseTools;

class Service extends Stream<ServiceEvent> {
    public static var instance(default, null) = new Service();

    public var bootstrap(default, null): Promise<Unit>;
    public var profile(default, null): Profile;

    function new() {
        super(function (_, _, _) {
            return function () {};
        });



        bootstrap = send(Get, "/api/profile")
                        .then(function (x) {
                            profile = x; //TODO
                            update(Initialized);
                        })
                        .map(function (_) return Unit._);
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
                    profile = null; //TODO
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

    public function findDatasets(page: UInt): Void {

    }



    function send(method: RequestMethod, url: String, ?data: Dynamic): Promise<Dynamic> {
        return JQuery.ajax(url, {type: method, dataType: "json", cache: false, data: data}).toPromise()
            .bind(function (response: ApiResponse) {
                return switch (response.status) {
                    case ApiStatus.OK:
                        Promise.resolved(response.data);
                    case ApiStatus.BadRequest:
                        Promise.rejected(new Error(""));
                    case ApiStatus.Unauthorized:
                        profile = null; //TODO
                        update(SignedOut);
                        Promise.rejected(new Error(""));
                    default:
                        Promise.resolved(new Error("response error"));
                }
            });
    }
}

enum ServiceEvent {
    Initialized;
    SignedIn;
    SignedOut;
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
    var BadRequest = "BadRequest";
    var Unauthorized = "Unauthorized";
    var Error = "Error";
}