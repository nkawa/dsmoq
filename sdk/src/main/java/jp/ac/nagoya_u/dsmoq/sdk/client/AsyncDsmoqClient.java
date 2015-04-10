package jp.ac.nagoya_u.dsmoq.sdk.client;

import jp.ac.nagoya_u.dsmoq.sdk.request.*;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;

public class AsyncDsmoqClient {
    private DsmoqClient client;

    public AsyncDsmoqClient(String baseUrl, String apiKey, String secretKey) {
        this.client = new DsmoqClient(baseUrl, apiKey, secretKey);
    }

    /**
     * Datasetを検索する。(GET /api/datasets相当)
     * @param param Dataset検索に使用するパラメタ
     * @return 検索結果のFuture
     */
    public CompletableFuture<RangeSlice<DatasetsSummary>> getDatasets(GetDatasetsParam param) {
        return CompletableFuture.supplyAsync(() -> client.getDatasets(param));
    }

    /**
     * Datasetを取得する。(GET /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @return 取得結果のFuture
     */
    public CompletableFuture<Dataset> getDataset(String datasetId) {
        return CompletableFuture.supplyAsync(() -> client.getDataset(datasetId));
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDatasetのFuture
     */
    public CompletableFuture<Dataset> createDataset(boolean saveLocal, boolean saveS3, File... files) {
        return CompletableFuture.supplyAsync(() -> client.createDataset(saveLocal, saveS3, files));
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @return 作成したDatasetのFuture
     */
    public CompletableFuture<Dataset> createDataset(String name, boolean saveLocal, boolean saveS3) {
        return CompletableFuture.supplyAsync(() -> client.createDataset(name, saveLocal, saveS3));
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDatasetのFuture
     */
    public CompletableFuture<Dataset> createDataset(String name, boolean saveLocal, boolean saveS3, File... files) {
        return CompletableFuture.supplyAsync(() -> client.createDataset(name, saveLocal, saveS3, files));
    }

    /**
     * Datasetにファイルを追加する。(POST /api/datasets/${dataset_id}/files相当)
     * @param datasetId DatasetID
     * @param files Datasetに追加するファイル(複数可)
     * @return 追加したファイルの情報のFuture
     */
    public CompletableFuture<DatasetAddFiles> addFiles(String datasetId, File... files) {
        return CompletableFuture.supplyAsync(() -> client.addFiles(datasetId, files));
    }

    /**
     * ファイルを更新する。(POST /api/datasets/${dataset_id}/files/${file_id}相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param file 更新対象のファイル
     * @return 更新されたファイル情報のFuture
     */
    public CompletableFuture<DatasetFile> updateFile(String datasetId, String fileId, File file) {
        return CompletableFuture.supplyAsync(() -> client.updateFile(datasetId, fileId, file));
    }

    /**
     * ファイル情報を更新する。(POST /api/datasets/${dataset_id}/files/${file_id}/metadata相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param param ファイル更新情報
     * @return 更新したファイル情報のFuture
     */
    public CompletableFuture<DatasetFile> updateFileMetaInfo(String datasetId, String fileId, UpdateFileMetaParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateFileMetaInfo(datasetId, fileId, param));
    }

    /**
     * データセットからファイルを削除する。（DELETE /api/datasets/${dataset_id}/files/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> deleteFile(String datasetId, String fileId) {
        return CompletableFuture.runAsync(() -> client.deleteFile(datasetId, fileId));
    }

    /**
     * データセットの情報を更新する。(PUT /api/datasets.${dataset_id}/metadata相当)
     * @param datasetId DatasetID
     * @param param データセット更新情報
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        return CompletableFuture.runAsync(() -> client.updateDatasetMetaInfo(datasetId, param));
    }

    /**
     * データセットに画像を追加する。（POST /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param files 追加する画像ファイル
     * @return 追加した画像情報のFuture
     */
    public CompletableFuture<DatasetAddImages> addImagesToDataset(String datasetId, File... files) {
        return CompletableFuture.supplyAsync(() -> client.addImagesToDataset(datasetId, files));
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。（PUT /api/datasets/${dataset_id}/image/primary相当）
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToDataset(datasetId, param));
    }


    /**
     * データセットに一覧で表示するメイン画像を設定する。
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> setPrimaryImageToDataset(String datasetId, File file) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToDataset(datasetId, file));
    }

    /**
     * データセットから画像を削除する。（DELETE /api/datasets/${dataset_id}/image/${image_id}相当）
     * @param datasetId DatasetID
     * @param imageId 画像ID
     * @return 画像削除後のデータセットのメイン画像情報のFuture
     */
    public CompletableFuture<DatasetDeleteImage> deleteImageToDataset(String datasetId, String imageId) {
        return CompletableFuture.supplyAsync(() -> client.deleteImageToDataset(datasetId, imageId));
    }

    /**
     * データセットの画像一覧を取得する。（GET /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットの画像一覧のFuture
     */
    public CompletableFuture<RangeSlice<DatasetGetImage>> getDatasetImage(String datasetId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getDatasetImage(datasetId, param));
    }

    /**
     * データセットのアクセス権一覧を取得する。（GET /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのアクセス権一覧のFuture
     */
    public CompletableFuture<RangeSlice<DatasetOwnership>> getAccessLevel(String datasetId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getAccessLevel(datasetId, param));
    }

    /**
     * データセットのアクセス権を変更する。（POST /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param params アクセス権制御情報
     * @return 変更後のアクセス権情報のFuture
     */
    public CompletableFuture<DatasetOwnerships> changeAccessLevel(String datasetId, List<SetAccessLevelParam> params) {
        return CompletableFuture.supplyAsync(() -> client.changeAccessLevel(datasetId, params));
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。（PUT /api/datasets/${dataset_id}/guest_access相当）
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        return CompletableFuture.runAsync(() -> client.changeGuestAccessLevel(datasetId, param));
    }

    /**
     * データセットを削除する。(DELETE /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> deleteDataset(String datasetId) {
        return CompletableFuture.runAsync(() -> client.deleteDataset(datasetId));
    }

    /**
     * データセットの保存先を変更する。(PUT /api/datasets/${dataset_id}/storage相当)
     * @param datasetId DatasetID
     * @param param 保存先変更情報
     * @return 変更タスクの情報のFuture
     */
    public CompletableFuture<DatasetTask> changeDatasetStorage(String datasetId, ChangeStorageParam param) {
        return CompletableFuture.supplyAsync(() -> client.changeDatasetStorage(datasetId, param));
    }

    /**
     * データセットをコピーします。（POST /api/datasets/${dataset_id}/copy相当）
     * @param datasetId DatasetID
     * @return コピーしたDatasetIDのFuture
     */
    public CompletableFuture<String> copyDataset(String datasetId) {
        return CompletableFuture.supplyAsync(() -> client.copyDataset(datasetId));
    }

    /**
     * CSVファイルからAttributeを読み込む。（POST /api/datasets/${dataset_id}/attributes/import相当）
     * @param datasetId DatasetID
     * @param file AttributeをインポートするCSVファイル
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> importAttribute(String datasetId, File file) {
        return CompletableFuture.runAsync(() -> client.importAttribute(datasetId, file));
    }

    /**
     * CSVファイルにAttributeを出力する。（GET /api/datasets/${dataset_id}/attributes/export相当）
     * @param datasetId DatasetID
     * @param downloadDirectory 出力先ディレクトリ
     * @return CSVファイルのFuture
     */
    public CompletableFuture<File> exportAttribute(String datasetId, String downloadDirectory) {
        return CompletableFuture.supplyAsync(() -> client.exportAttribute(datasetId, downloadDirectory));
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。（PUT /api/datasets/${dataset_id}/image/${image_id}/featured相当）
     * @param datasetId DatasetID
     * @param imageId 指定する画像ID
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> setFeaturedImageToDataset(String datasetId, String imageId) {
        return CompletableFuture.runAsync(() -> client.setFeaturedImageToDataset(datasetId, imageId));
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> setFeaturedImageToDataset(String datasetId, File file) {
        return CompletableFuture.runAsync(() -> client.setFeaturedImageToDataset(datasetId, file));
    }

    /**
     * データセットからファイルをダウンロードする。（GET /files/${dataset_id}/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル情報のFuture
     */
    public CompletableFuture<File> downloadFile(String datasetId, String fileId, String downloadDirectory) {
        return CompletableFuture.supplyAsync(() -> client.downloadFile(datasetId, fileId, downloadDirectory));
    }

    /**
     * データセットから一時ディレクトリにファイルをダウンロードする。
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return ダウンロードしたファイルのFuture
     */
    public CompletableFuture<File> downloadFile(String datasetId, String fileId) {
        return CompletableFuture.supplyAsync(() -> client.downloadFile(datasetId, fileId));
    }

    /**
     * データセットからすべてのファイルをダウンロードする。
     * @param datasetId DatasetID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイルのFuture
     */
    public CompletableFuture<List<File>> downloadAllFiles(String datasetId, String downloadDirectory) {
        return CompletableFuture.supplyAsync(() -> client.downloadAllFiles(datasetId, downloadDirectory));
    }

    /**
     * データセットから一時ディレクトリにすべてのファイルをダウンロードする。
     * @param datasetId DatasetID
     * @return ダウンロードしたファイルのFuture
     */
    public CompletableFuture<List<File>> downloadAllFiles(String datasetId) {
        return CompletableFuture.supplyAsync(() -> client.downloadAllFiles(datasetId));
    }

    /**
     * データセットからすべてのファイルをダウンロードする。Zipファイルは圧縮されたファイルのみを取得する。
     * @param datasetId DatasetID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイルのFuture
     */
    public CompletableFuture<List<File>> downloadAllExpandedFiles(String datasetId, String downloadDirectory) {
        return CompletableFuture.supplyAsync(() -> client.downloadAllExpandedFiles(datasetId, downloadDirectory));
    }

    /**
     * データセットから一時ディレクトリにすべてのファイルをダウンロードする。Zipファイルは圧縮されたファイルのみを取得する。
     * @param datasetId DatasetID
     * @return ダウンロードしたファイルのFuture
     */
    public CompletableFuture<List<File>> downloadAllExpandedFiles(String datasetId) {
        return CompletableFuture.supplyAsync(() -> client.downloadAllExpandedFiles(datasetId));
    }

    /**
     * グループ一覧を取得する。（GET /api/groups相当）
     * @param param グループ一覧取得情報
     * @return グループ一覧情報のFuture
     */
    public CompletableFuture<RangeSlice<GroupsSummary>> getGroups(GetGroupsParam param) {
        return CompletableFuture.supplyAsync(() -> client.getGroups(param));
    }

    /**
     * グループ詳細を取得する。（GET /api/groups/${group_id}相当）
     * @param groupId グループID
     * @return グループ詳細情報のFuture
     */
    public CompletableFuture<Group> getGroup(String groupId) {
        return CompletableFuture.supplyAsync(() -> client.getGroup(groupId));
    }

    /**
     * グループのメンバー一覧を取得する。（GET /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param グループメンバー一覧取得情報
     * @return グループメンバー一覧情報のFuture
     */
    public CompletableFuture<RangeSlice<MemberSummary>> getMembers(String groupId, GetMembersParam param) {
        return CompletableFuture.supplyAsync(() -> client.getMembers(groupId, param));
    }

    /**
     * グループを作成する。（POST /api/groups相当）
     * @param param グループ作成情報
     * @return 作成したグループ詳細情報のFuture
     */
    public CompletableFuture<Group> createGroup(CreateGroupParam param) {
        return CompletableFuture.supplyAsync(() -> client.createGroup(param));
    }

    /**
     * グループ詳細情報を更新する。（PUT /api/groups/${group_id}相当）
     * @param groupId グループID
     * @param param グループ詳細更新情報
     * @return グループ詳細情報のFuture
     */
    public CompletableFuture<Group> updateGroup(String groupId, UpdateGroupParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateGroup(groupId, param));
    }

    /**
     * グループの画像一覧を取得する。（GET /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param param 一覧取得情報
     * @return グループの画像一覧情報のFuture
     */
    public CompletableFuture<RangeSlice<GroupGetImage>> getGroupImage(String groupId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getGroupImage(groupId, param));
    }

    /**
     * グループに画像を追加する。（POST /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param files 画像ファイル
     * @return 追加した画像ファイル情報のFuture
     */
    public CompletableFuture<GroupAddImages> addImagesToGroup(String groupId, File... files) {
        return CompletableFuture.supplyAsync(() -> client.addImagesToGroup(groupId, files));
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。（PUT /api/groups/${group_id}/images/primary相当）
     * @param groupId グループID
     * @param param メイン画像指定情報
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToGroup(groupId, param));
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     * @param groupId グループID
     * @param file 画像ファイル
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> setPrimaryImageToGroup(String groupId, File file) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToGroup(groupId, file));
    }

    /**
     * グループから画像を削除する。（DELETE /api/groups/${group_id}/images/${image_id}相当）
     * @param groupId グループID
     * @param imageId 画像ID
     * @return 画像削除後のグループのメイン画像情報のFuture
     */
    public CompletableFuture<GroupDeleteImage> deleteImageToGroup(String groupId, String imageId) {
        return CompletableFuture.supplyAsync(() -> client.deleteImageToGroup(groupId, imageId));
    }

    /**
     * グループにメンバーを追加する。（POST /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param メンバー追加情報
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> addMember(String groupId, List<AddMemberParam> param) {
        return CompletableFuture.runAsync(() -> client.addMember(groupId, param));
    }

    /**
     * メンバーのロールを設定する。（PUT /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     * @param param ロール設定情報
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> setMemberRole(String groupId, String userId, SetMemberRoleParam param) {
        return CompletableFuture.runAsync(() -> client.setMemberRole(groupId, userId, param));
    }

    /**
     * メンバーを削除する。（DELETE /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> deleteMember(String groupId, String userId) {
        return CompletableFuture.runAsync(() -> client.deleteMember(groupId, userId));
    }

    /**
     * グループを削除する。（DELETE /api/groups/${group_id}相当）
     * @param groupId グループID
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> deleteGroup(String groupId) {
        return CompletableFuture.runAsync(() -> client.deleteGroup(groupId));
    }

    /**
     * ログインユーザのプロファイルを取得する。（GET /api/profile相当）
     * @return プロファイルのFuture
     */
    public CompletableFuture<User> getProfile() {
        return CompletableFuture.supplyAsync(() -> client.getProfile());
    }

    /**
     * ログインユーザのプロファイルを更新する。（PUT /api/profile相当）
     * @param param プロファイル更新情報
     * @return プロファイルのFuture
     */
    public CompletableFuture<User> updateProfile(UpdateProfileParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateProfile(param));
    }

    /**
     * ログインユーザの画像を更新する。（POST /api/profile/image相当）
     * @param file 画像ファイル
     * @return プロファイルのFuture
     */
    public CompletableFuture<User> updateProfileIcon(File file) {
        return CompletableFuture.supplyAsync(() -> client.updateProfileIcon(file));
    }

    /**
     * ログインユーザのE-Mailを変更する。（POST /api/profile/email_change_request相当）
     * @param param E-Mail変更情報
     * @return プロファイルのFuture
     */
    public CompletableFuture<User> updateEmail(UpdateEmailParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateEmail(param));
    }

    /**
     * ログインユーザのパスワードを変更する。（PUT /api/profile/password相当）
     * @param param パスワード変更情報
     * @return 実行結果のFuture
     */
    public CompletableFuture<Void> changePassword(ChangePasswordParam param) {
        return CompletableFuture.runAsync(() -> client.changePassword(param));
    }

    /**
     * ユーザー一覧を取得する。（GET /api/accounts相当）
     * @return ユーザー一覧のFuture
     */
    public CompletableFuture<List<User>> getAccounts() {
        return CompletableFuture.supplyAsync(() -> client.getAccounts());
    }

    /**
     * ライセンス一覧を取得する。（GET /api/licenses相当）
     * @return ライセンス一覧情報のFuture
     */
    public CompletableFuture<List<License>> getLicenses() {
        return CompletableFuture.supplyAsync(() -> client.getLicenses());
    }

    /**
     * タスクの現在のステータスを取得する。（GET /api/tasks/${task_id}相当）
     * @param taskId タスクID
     * @return タスクのステータス情報のFuture
     */
    public CompletableFuture<TaskStatus> getTaskStatus(String taskId) {
        return CompletableFuture.supplyAsync(() -> client.getTaskStatus(taskId));
    }

    /**
     * 統計情報を取得します。（GET /api/statistics相当）
     * @param param 統計情報期間指定
     * @return 統計情報のFuture
     */
    public CompletableFuture<List<StatisticsDetail>> getStatistics(StatisticsParam param) {
        return CompletableFuture.supplyAsync(() -> client.getStatistics(param));
    }
}