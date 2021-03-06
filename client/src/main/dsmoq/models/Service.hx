package dsmoq.models;

import haxe.ds.Option;
import haxe.Json;
import js.Cookie;
import js.Error;
import hxgnd.Unit;
import hxgnd.LangTools;
import hxgnd.Promise;
import hxgnd.PositiveInt;
import hxgnd.Stream;
import hxgnd.js.JQuery;
import hxgnd.js.JqHtml;

import dsmoq.pages.Notification;

class Service extends Stream<ServiceEvent> {
    public static inline var QueryLimit: UInt = 20;

    private static inline var GUEST_HEADER_NAME: String = "X-Dsmoq-Guest";

    public static var instance(default, null) = new Service();

    public var bootstrap(default, null): Promise<Unit>;
    public var profile(default, null): Profile;
    public var licenses(default, null): Array<License>;

    function new() {
        super(function (_) {
            return function () {};
        });

        profile = guest();
        licenses = [];

        var profilePromice = new Promise(function(context) {
            send(Get, "/api/profile").then(
                function (x) {
                    profile = x;
                    context.fulfill(Unit._);
                },
                function(_) {
                    if (Cookie.get("user.disabled") == "true") {
                        Cookie.remove("user.disabled");
                        context.fulfill(Unit._);
                    } else {
                        signout().then(function(_) {
                            Cookie.set("user.disabled", "true");
                            js.Browser.location.href = "/";
                        });
                    }
                }
            );
        });
        bootstrap = Promise.all([
            profilePromice,
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

    public function updateProfile(
        name: String,
        fullname: String,
        organization: String,
        title: String,
        description: String
    ): Promise<Profile> {
        return send(Put, "/api/profile", {
            name: name,
            fullname: fullname,
            organization: organization,
            title: title,
            description: description
        }).then(function (x: Profile) {
            profile = x;
            update(ProfileUpdated);
        });
    }

    public function updateImage(form: JqHtml): Promise<Profile> {
        return sendForm(Post, "/api/profile/image", form).then(function (x: Profile) {
            profile = x;
            update(ProfileUpdated);
        });
    }

    public function sendEmailChangeRequests(email: String): Promise<Unit> {
        return send(Post, "/api/profile/email_change_requests", { email: email });
    }

    public function updatePassword(currentPassword: String, newPassword: String): Promise<Unit> {
        return send(Put, "/api/profile/password", { currentPassword: currentPassword, newPassword: newPassword });
    }

    // ---
    public function createDataset(form: JqHtml, saveLocal: Bool, saveS3: Bool): Promise<{id: String}> {
        return sendForm(Post, "/api/datasets", form, { saveLocal: saveLocal, saveS3: saveS3 });
    }

    public function searchDatasets(
        ?params: {?query: Dynamic, ?offset: Int, ?limit: Int }
    ): Promise<RangeSlice<DatasetSummary>> {
        return send(Get, "/api/datasets", params);
    }

    public function findDatasets(?params: {?query: String,
                                           ?owners: Array<String>,
                                           ?groups: Array<String>,
                                           ?attributes: Array<DatasetAttribute>,
                                           ?offset: Int,
                                           ?limit: Int,
                                           ?orderby: String
                                           }): Promise<RangeSlice<DatasetSummary>> {
        return send(Get, "/api/datasets", params);
    }

    public function getDataset(datasetId: String): Promise<Dataset> {
        return send(Get, '/api/datasets/$datasetId').map(function (a) {
            return {
                id: cast a.id,
                files: cast a.files,
                meta: cast a.meta,
                images: cast a.images,
                primaryImage: cast Lambda.find(a.images, function (x) return x.id == a.primaryImage),
                featuredImage: cast Lambda.find(a.images, function (x) return x.id == a.featuredImage),
                ownerships: cast a.ownerships,
                defaultAccessLevel: cast a.defaultAccessLevel,
                permission: cast a.permission,
                accessCount: a.accessCount,
                localState: a.localState,
                s3State: a.s3State,
                filesCount: a.filesCount,
                fileLimit: a.fileLimit,
				app: a.app,
				appUrl: a.appUrl
            };
        });
    }

    public function addDatasetFiles(datasetId: String, form: JqHtml): Promise<Array<DatasetFile>> {
        return sendForm(Post, '/api/datasets/$datasetId/files', form).map(function (res) {
            return cast res.files;
        });
    }

    public function replaceDatasetFile(datasetId: String, fileId: String, form: JqHtml): Promise<DatasetFile> {
        return sendForm(Post, '/api/datasets/$datasetId/files/$fileId', form);
    }

    public function updateDatatetFileMetadata(
        datasetId: String,
        fileId: String,
        name: String,
        description: String
    ): Promise<DatasetFile> {
        return send(Put, '/api/datasets/$datasetId/files/$fileId/metadata', { name: name, description: description });
    }

    public function removeDatasetFile(datasetId: String, fileId: String): Promise<Unit> {
        return send(Delete, '/api/datasets/$datasetId/files/$fileId');
    }

    public function updateDatasetMetadata(datasetId: String, metadata: DatasetMetadata): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/metadata', metadata);
    }

    //public function addDatasetImage(datasetId: String, form: JqHtml): Promise<{images: Array<Image>, primaryImage: String}> {
        //return sendForm(Post, '/api/datasets/$datasetId/images', form);
    //}
//
    //public function setDatasetPrimaryImage(datasetId: String, imageId: String): Promise<Unit> {
        //return send(Put, '/api/datasets/$datasetId/images/primary', {id: imageId});
    //}
//
    public function changeDatasetImage(datasetId: String, form: JqHtml): Promise<{images: Array<Image>, primaryImage: String}> {
        // TODO 既存イメージ削除
        return sendForm(Post, '/api/datasets/$datasetId/images', form).flatMap(function (res) {
            return send(Put, '/api/datasets/$datasetId/images/primary', { imageId: res.images[0].id } ).map(function (_) {
                return { images: cast res.images, primaryImage: cast res.images[0].id };
            });
        });
    }
    
    public function setDatasetImagePrimary(datasetId: String, imageId: String): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/images/primary', { imageId: imageId } );
    }
    
    public function addDatasetImage(datasetId: String, form: JqHtml): Promise<{images: Array<Image>, primaryImage: String}> {
        return sendForm(Post, '/api/datasets/$datasetId/images', form);
    }

    public function getDatasetImage(datasetId: String, ?params: { ?limit: Int, ?offset: Int } ): Promise<RangeSlice<DatasetImage>> {
        return send(Get, '/api/datasets/$datasetId/images', params);
    }

    public function removeDatasetImage(datasetId: String, imageId: String): Promise<{primaryImage: String, featuredImage: String}> {
        return send(Delete, '/api/datasets/$datasetId/images/$imageId');
    }

    // TODO パラメータの見直し
    public function updateDatasetACL(datasetId: String, acl: Array<{id: String, ownerType: DatasetOwnershipType, accessLevel: DatasetPermission}>): Promise<Unit> {
        return send(Post, '/api/datasets/$datasetId/acl', acl);
    }

    // setで代用可能
    //public function removeDatasetAccessLevel(datasetId: String, groupId: String): Promise<Unit> {
        //return send(Delete, '/api/datasets/$datasetId/acl/$groupId');
    //}

    public function setDatasetGuestAccessLevel(datasetId: String, accessLevel: DatasetGuestAccessLevel): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/guest_access', {accessLevel: accessLevel});
    }

    // setで代用可能
    //public function removeDatasetGuestAccessLevel(dataset: String): Promise<Unit> {
        //return send(Delete, '/api/datasets/$datasetId/acl/guest');
    //}

    public function deleteDeataset(datasetId: String): Promise<Unit> {
        return send(Delete, '/api/datasets/$datasetId');
    }
    
    public function changeDatasetStorage(datasetId: String, saveLocal: Bool, saveS3: Bool): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/storage', { saveLocal: saveLocal, saveS3: saveS3 });
    }

    public function getDatasetApp(datasetId: String): Promise<RangeSlice<DatasetApp>> {
        return send(Get, '/api/datasets/${datasetId}/app');
    }

    public function addDatasetApp(datasetId: String, description: String, form: JqHtml): Promise<DatasetApp> {
        return sendForm(Post, '/api/datasets/${datasetId}/app', form, { description: description });
    }

    public function updateDatasetApp(datasetId: String, appId: String, description: String, form: JqHtml): Promise<DatasetApp> {
        return sendForm(Post, '/api/datasets/${datasetId}/app', form, { appId: appId, description: description });
    }

    public function removeDatasetApp(datasetId: String): Promise<Unit> {
        return send(Delete, '/api/datasets/${datasetId}/app');
    }

    // ---
    public function getDatasetQueries(): Promise<Array<DatasetQuery>> {
        return send(Get, '/api/dataset_queries');
    }

    public function addDatasetQuery(name: String, query: Dynamic): Promise<DatasetQuery> {
        return send(Post, '/api/dataset_queries', { name: name, query: query });
    }

    public function deleteDatasetQuery(id: String): Promise<Unit> {
        return send(Delete, '/api/dataset_queries/${id}');
    }

    // ---
    public function createGroup(name: String): Promise<Group> {
        // TODO descriptionをAPIパラメータから削除
        return send(Post, "/api/groups", { name: name, description: "" });
    }

    public function findGroups(?params: {?query: String,
                                         ?user: String,
                                         ?offset: Int,
                                         ?limit: Int}): Promise<RangeSlice<DatasetSummary>> {
        return send(Get, "/api/groups", params);
    }

    public function getGroup(groupId: String): Promise<Group> {
        return send(Get, '/api/groups/$groupId').map(function (a) {
            return {
                id: cast a.id,
                name: cast a.name,
                description: cast a.description,
                images: cast a.images,
                primaryImage: cast Lambda.find(a.images, function (x) return x.id == a.primaryImage),
                isMember: cast a.isMember,
                role: cast a.role,
                providedDatasetCount: cast a.providedDatasetCount
            };
        });
    }

    public function getGroupMembers(groupId: String, ?params: { ?offset: Int, ?limit: Int })
            : Promise<RangeSlice<GroupMember>> {
        var params = LangTools.orElse(params, {});
        return send(Get, '/api/groups/$groupId/members', { offset: LangTools.orElse(params.offset, 0),
                                                           limit: LangTools.orElse(params.limit, QueryLimit) });
    }

    public function updateGroupBasics(groupId: String, name: String, description: String): Promise<Group> {
        return send(Put, '/api/groups/$groupId', { name: name, description: description });
    }

    public function changeGroupImage(groupId: String, form: JqHtml): Promise<{images: Array<Image>, primaryImage: String}> {
        // TODO 既存イメージ削除
        return sendForm(Post, '/api/groups/$groupId/images', form).flatMap(function (res) {
            return send(Put, '/api/groups/$groupId/images/primary', { imageId: res.images[0].id } ).map(function (_) {
                return { images: cast res.images, primaryImage: cast res.images[0].id };
            });
        });

    }

    public function getGroupImage(groupId: String, ?params: { ?limit: Int, ?offset: Int } ): Promise<RangeSlice<GroupImage>> {
        return send(Get, '/api/groups/$groupId/images', params);
    }
    
    public function addGroupImages(groupId: String, form: JqHtml): Promise<Unit> {
        return sendForm(Post, '/api/groups/$groupId/images', form);
    }

    public function setGroupPrimaryImage(groupId: String, imageId: String): Promise<Unit> {
        return send(Put, '/api/groups/$groupId/images/primary', {imageId: imageId});
    }

    public function removeGroupImage(groupId: String, imageId: String): Promise<{primaryImage: String}> {
        return send(Delete, '/api/groups/$groupId/images/$imageId');
    }

    public function addGroupMember(groupId: String, members: Array<{userId: String, role: GroupRole}>) : Promise<Unit> {
        return send(Post, '/api/groups/$groupId/members', members);
    }

    public function updateGroupMemberRole(groupId: String, memberId: String, role: GroupRole): Promise<Unit> {
        return send(Put, '/api/groups/$groupId/members/$memberId', { role: role });
    }

    public function removeGroupMember(groupId: String, memberId: String): Promise<Unit> {
        return send(Delete, '/api/groups/$groupId/members/$memberId');
    }

    //public function updateGroupMemberRoles(groupId: String, memberRoles: Array<{id: String, role: GroupRole}>): Promise<Unit> {
        //return send(Post, '/api/groups/$groupId/members', memberRoles);
    //}

    public function deleteGroup(groupId: String): Promise<Unit> {
        return send(Delete, '/api/groups/$groupId');
    }


    public function findUsers(?params: { ?query: String, ?limit: Int, ?offset: Int })
            : Promise<Array<User>> {
        return send(Get, "/api/suggests/users", params);
    }
    
    public function getStatistics(?params :{ ?from: Date, ?to: Date } ) : Promise<Array<StatisticsDetail>> {
        return send(Get, "/api/statistics", params);
    }
    
    public function copyDataset(datasetId: String): Promise<DatasetCopyId> {
        return send(Post, '/api/datasets/$datasetId/copy');
    }
    
    public function findUsersAndGroups(?params: { ?query: String, ?limit: Int, ?offset: Int, ?excludeIds: Array<String> } ) : Promise<Array<SuggestedOwner>>
    {
        return send(Get, "/api/suggests/users_and_groups", params);
    }
    
    public function getOwnerships(datasetId: String, ?params: { ?limit: Int, ?offset: Int } ) : Promise<RangeSlice<DatasetOwnership>>
    {
        return send(Get, '/api/datasets/$datasetId/acl', params);
    }
    
    public function setDatasetImageFeatured(datasetId: String, imageId: String): Promise<Unit> {
        return send(Put, '/api/datasets/$datasetId/images/featured', { imageId: imageId });
    }
    
    public function getTags() : Promise<Array<String>> {
        return send(Get, '/api/tags');
    }
    
    public function getTagColors() : Promise<Array<TagColor>> {
        return send(Get, '/api/tags/color');
    }
    
    public function getMessage() : Promise<String> {
        return send(Get, '/api/message');
    }

    public function getDatasetFiles(datasetId: String, ?params: { ?limit: Int, ?offset: Int } ) : Promise<RangeSlice<DatasetFile>>
    {
        return send(Get, '/api/datasets/$datasetId/files', params);
    }

    public function getDatasetZippedFiles(datasetId: String, fileId: String, ?params: { ?limit: Int, ?offset: Int } ) : Promise<RangeSlice<DatasetZipedFile>>
    {
        return send(Get, '/api/datasets/$datasetId/files/$fileId/zippedfiles', params);
    }

    inline function guest(): Profile {
        return {
            id: "",
            name: "",
            fullname: "",
            organization: "",
            title: "",
            description: "",
            image: "",
            mailAddress: "",
            isGuest: true,
            isDisabled: false,
            isGoogleUser: false,
        }
    }

    /**
     * AJAXを送信する。
     *
     * @param method HTTPメソッド
     * @param url 送信するAPIのURL
     * @param data 送信するデータ
     * @return 処理結果のPromise
     */
    function send<T>(method: RequestMethod, url: String, ?data: {}): Promise<T> {
        var str: String = method;

        var d = if (data == null) {
            null;
        } else {
            { d: Json.stringify(data) };
        }

        var xhr = JQuery.ajax(url, { type: str, dataType: "json", cache: false, data: d });
        return xhr.always(function() {
            if (!profile.isGuest) {
                if (xhr.getResponseHeader(GUEST_HEADER_NAME) == "true") {
                    // ログインしている状態でisGuestヘッダがtrueの場合、
                    // セッションタイムアウトによってサーバー側ではログアウトしているが、
                    // クライアント側ではログイン情報が更新されていない。
                    // その為、クライアント側のログイン情報をゲスト化する必要がある。
                    profile = guest();
                    // タイムアウト処理(再描画)を行う
                    update(SessionTimeout);
                }
            }
        })
        .fail(function(err: Dynamic) {
            trace(err);
            switch (err.status) {
                case 400: // BadRequest
                    switch (err.responseJSON.status) {
                        case ApiStatus.IllegalArgument:
                            Notification.show("error", trimD(err.responseJSON.data.value));
                        case _:
                            Notification.show("error", trimD(err.responseJSON.data));
                    }
                case 404: // NotFound
                    Notification.show("error", "NotFound");
                case 403: // Forbidden
                    switch (err.responseJSON.status) {
                        case ApiStatus.Unauthorized:
                            if (!profile.isGuest) {
                                // ログインしている状態でUnauthorizedを受け取った場合、
                                // セッションタイムアウトによってサーバー側ではログアウトしているが、
                                // クライアント側ではログイン情報が更新されていない。
                                // その為、クライアント側のログイン情報をゲスト化する必要がある。
                                profile = guest();
                                // サインアウト処理(TOPへリダイレクト)を行う
                                update(SignedOut);
                            }
                    }
                    Notification.show("error", trimD(err.responseJSON.data));
                case _: // その他(500系など)
                    Notification.show("error", "error happened");
            }
        }).toPromise().flatMap(function (response: ApiResponse) {
            trace(response);
            return Promise.fulfilled(cast response.data);
        });
    }

    /**
     * Formを送信する。
     *
     * @param method HTTPメソッド
     * @param url 送信先のAPIのURL
     * @param form 送信するForm
     * @param optData 送信するデータ
     * @return 処理結果のPromise
     */
    function sendForm<T>(method: RequestMethod, url: String, form: JqHtml, ?optData: {}): Promise<T> {
        return new Promise(function (ctx) {
            untyped form.ajaxSubmit({
                url: url,
                type: method,
                dataType: "json",
                data: optData,
                success: function (response) {
                    trace(response);
                    switch (response.status) {
                        case ApiStatus.OK:
                            ctx.fulfill(cast response.data);
                        case _:
                            ctx.reject(new ServiceError("Unknown", Unknown));
                    }
                },
                error: function(err) {
                    trace(err);
                    ctx.reject(cast err);
                    switch (err.status) {
                        case 400: // BadRequest
                            switch (err.responseJSON.status) {
                                case ApiStatus.IllegalArgument:
                                    Notification.show("error", trimD(err.responseJSON.data.value));
                                case _:
                                    Notification.show("error", trimD(err.responseJSON.data));
                            }
                        case 404: // NotFound
                            Notification.show("error", "NotFound");
                        case 403: // Forbidden
                            switch (err.responseJSON.status) {
                                case ApiStatus.Unauthorized:
                                    if (!profile.isGuest) {
                                        // ログインしている状態でUnauthorizedを受け取った場合、
                                        // セッションタイムアウトによってサーバー側ではログアウトしているが、
                                        // クライアント側ではログイン情報が更新されていない。
                                        // その為、クライアント側のログイン情報をゲスト化する必要がある。
                                        profile = guest();
                                        update(SignedOut);
                                    }
                            }
                            Notification.show("error", trimD(err.responseJSON.data));
                        case _: // その他(500系など)
                            Notification.show("error", "error happened");
                    }
                }
            });
        });
    }

    /**
     * パラメータから"d."を削り、表示用に整形する。
     *
     * @param str 整形対象
     * @return 整形後の文字列
     */
    function trimD(str: String): String {
        return StringTools.replace(str, "d.", "");
    }
}

enum ServiceEvent {
    SignedIn;
    SignedOut;
    ProfileUpdated;
    SessionTimeout;
}

@:enum abstract ServiceErrorType(String) to String {
    var NotFound = "NotFoundError";
    var BadRequest = "BadRequestError";
    var Unauthorized = "UnauthorizedError";
    var Unknown = "Unknown";
}

class ServiceError extends Error {
    public var detail(default, null): Array<{name: String, message: String}>;

    public function new(message:String, type: ServiceErrorType, ?detail: Array<{name: String, message: String}>) {
        super(message);
        this.name = type;
        this.detail = detail;
    }
}

@:enum private abstract RequestMethod(String) to String {
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
