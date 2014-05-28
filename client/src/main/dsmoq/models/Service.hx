package dsmoq.models;

import js.Cookie;
import js.Error;
import js.jqhx.JqHtml;
import js.jqhx.JQuery;
import js.support.ControllablePromise;
import js.support.Promise;
import js.support.Stream;
import js.support.Unit;
import dsmoq.framework.types.PositiveInt;

using dsmoq.framework.helper.JQueryTools;
using dsmoq.framework.helper.LangHelper;

class Service extends Stream<ServiceEvent> {
    public static inline var QueryLimit: UInt = 20;

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

    public function updateProfile(form: JqHtml): Promise<Profile> {
        return sendForm("/api/profile", form).then(function (x: Profile) {
            profile = x;
            update(SignedIn); //TODO updateイベント
        });
    }

    public function sendEmailChangeRequests(email: String): Promise<Unit> {
        return send(Post, "/api/profile/email_change_requests", { email: email });
    }

    public function updatePassword(currentPassword: String, newPassword: String): Promise<Unit> {
        return send(Put, "/api/profile/password", { current_password: currentPassword, new_password: newPassword });
    }

    // ---
    public function createDataset(form: JqHtml): Promise<{id: String}> {
        return sendForm("/api/datasets", form);
    }

    public function findDatasets(?params: {?query: String,
                                           ?group: String,
                                           ?owner: String,
                                           ?attributes: Array<DatasetAttribute>,
                                           ?page: PositiveInt}): Promise<RangeSlice<DatasetSummary>> {
        var params = params.orElse({});
        return send(Get, "/api/datasets", { offset: toOffset(params.page.orElse(1)), limit: QueryLimit });
    }

    public function getDataset(datasetId: String): Promise<Dataset> {
        return send(Get, '/api/datasets/$datasetId').map(function (a) {
            return {
                id: cast a.id,
                files: cast a.files,
                meta: cast a.meta,
                images: cast a.images,
                primaryImage: cast Lambda.find(a.images, function (x) return x.id == a.primaryImage),
                ownerships: cast a.ownerships,
                defaultAccessLevel: cast a.defaultAccessLevel,
                permission: cast a.permission
            };
        });
    }

    public function addDatasetFiles(datasetId: String, form: JqHtml): Promise<Array<DatasetFile>> {
        return sendForm('/api/datasets/$datasetId/files', form);
    }

    public function replaceDatasetFile(datasetId: String, fileId: String, form: JqHtml): Promise<DatasetFile> {
        return sendForm('/api/datasets/$datasetId/files/$fileId', form);
    }

    public function renameDatatetFile(datasetId: String, fileId: String, name: String): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/files/$fileId/name', name);
    }

    public function removeDatasetFile(datasetId: String, fileId: String): Promise<Unit> {
        return send(Delete, '/api/datasets/$datasetId/files/$fileId');
    }

    public function updateDatasetMetadata(datasetId: String, metadata: DatasetMetadata): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/metadata', {
            name: metadata.name,
            description: metadata.description,
            "attributes[][name]": metadata.attributes.map(function (x) return x.name),
            "attributes[][value]": metadata.attributes.map(function (x) return x.value),
            license: metadata.license
        });
    }

    public function addDatasetImage(datasetId: String, form: JqHtml): Promise<{images: Array<Image>, primaryImage: String}> {
        return sendForm('/api/datasets/$datasetId/images', form);
    }

    public function setDatasetPrimaryImage(datasetId: String, imageId: String): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/images/primary', {id: imageId});
    }

    public function removeDatasetImage(datasetId: String, imageId: String): Promise<{primaryImage: String}> {
        return send(Delete, '/api/datasets/$datasetId/images/$imageId');
    }

    public function setDatasetAccessLevel(datasetId: String, groupId: String, accessLevel: DatasetPermission): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/acl/$groupId', accessLevel);
    }

    // setで代用可能
    //public function removeDatasetAccessLevel(datasetId: String, groupId: String): Promise<Unit> {
        //return send(Delete, '/api/datasets/$datasetId/acl/$groupId');
    //}

    public function setDatasetGuestAccessLevel(datasetId: String, accessLevel: GuestAccessLevel): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/acl/guest', accessLevel);
    }

    // setで代用可能
    //public function removeDatasetGuestAccessLevel(dataset: String): Promise<Unit> {
        //return send(Delete, '/api/datasets/$datasetId/acl/guest');
    //}

    public function deleteDeataset(datasetId: String): Promise<Unit> {
        return send(Delete, '/api/datasets/$datasetId');
    }

    // ---
    public function createGroup(name: String): Promise<{id: String}> {
        // TODO descriptionをAPIパラメータから削除
        return send(Post, "/api/groups", { name: name, description: "" });
    }

    public function findGroups(?params: {page: UInt}): Promise<RangeSlice<Group>> {
        // TODO ページング
        return send(Get, "/api/groups", { offset: 0, limit: 20 });
    }

    public function getGroup(groupId: String): Promise<Group> {
        return send(Get, '/api/groups/$groupId');
    }

    public function getGroupMembers(groupId: String, page: UInt): Promise<RangeSlice<GroupMember>> {
        // TODO ページング
        return send(Get, '/api/groups/$groupId/members', { offset: 0, limit: 20 });
    }

    public function updateGroupBasics(groupId: String, name: String, description: String): Promise<Group> {
        return send(Post, '/api/groups/$groupId', { name: name, description: description });
    }

    public function addGroupImages(groupId: String, form: JqHtml): Promise<Unit> {
        return null;
    }

    public function setGroupPrimaryImage(groupId: String, imageId: String): Promise<Unit> {
        return null;
    }

    public function removeGroupImage(groupId: String, imageId: String): Promise<Unit> {
        return null;
    }

    public function addGroupMember(groupId: String, userId: String, role: Int): Promise<Unit> {
        return null;
    }

    public function setGroupMemberRole(groupId: String, userId: String, role: Int): Promise<Unit> {
        return null;
    }

    public function removeGroupMember(groupId: String, userId: String): Promise<Unit> {
        return null;
    }

    public function deleteGroup(groupId: String): Promise<Unit> {
        return null;
    }


    public function getUsers(): Promise<{}> {
        return null;
    }



    inline function guest(): Profile {
        return {
            id: "",
            name: "",
            fullname: "",
            organization: "",
            title: "",
            image: "",
            email: "",
            isGuest: true
        }
    }

    inline function toOffset(page: PositiveInt): Int {
        return (page - 1) * QueryLimit;
    }

    function send<T>(method: RequestMethod, url: String, ?data: Dynamic): Promise<T> {
        return JQuery.ajax(url, {type: method, dataType: "json", cache: false, data: data, traditional: true}).toPromise()
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

    function sendForm<T>(url: String, form: JqHtml, ?optData: {}): Promise<T> {
        var promise = new ControllablePromise();
        untyped form.ajaxSubmit({
            url: url,
            type: "post",
            dataType: "json",
            data: optData,
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