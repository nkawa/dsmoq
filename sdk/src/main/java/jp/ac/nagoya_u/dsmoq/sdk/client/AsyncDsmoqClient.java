package jp.ac.nagoya_u.dsmoq.sdk.client;

import jp.ac.nagoya_u.dsmoq.sdk.request.AddMemberParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.ChangePasswordParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.ChangeStorageParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.CreateGroupParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetDatasetsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetGroupsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetMembersParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetAccessLevelParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetGuestAccessLevelParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetMemberRoleParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetPrimaryImageParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.StatisticsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateDatasetMetaParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateEmailParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateFileMetaParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateGroupParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateProfileParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.Dataset;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetAddFiles;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetAddImages;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetDeleteImage;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFileContent;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetGetImage;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetOwnership;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetOwnerships;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetTask;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetZipedFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetsSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.Group;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupAddImages;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupDeleteImage;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupGetImage;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupsSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.License;
import jp.ac.nagoya_u.dsmoq.sdk.response.MemberSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.RangeSlice;
import jp.ac.nagoya_u.dsmoq.sdk.response.StatisticsDetail;
import jp.ac.nagoya_u.dsmoq.sdk.response.TaskStatus;
import jp.ac.nagoya_u.dsmoq.sdk.response.User;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 非同期にdsmoq APIを叩くためのクライアントクラス
 * 個々のWeb APIの仕様については、APIのドキュメントを参照してください。
 */
public class AsyncDsmoqClient {
    private DsmoqClient client;

    /**
     * クライアントオブジェクトを生成する。
     * @param baseUrl 基準となるURL
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     */
    public AsyncDsmoqClient(String baseUrl, String apiKey, String secretKey) {
        this.client = DsmoqClient.create(baseUrl, apiKey, secretKey);
    }

    /**
     * Datasetを検索する。(GET /api/datasets相当)
     * @param param Dataset検索に使用するパラメタ
     * @return 検索結果のCompletableFuture
     * @see DsmoqClient#getDatasets(GetDatasetsParam)
     */
    public CompletableFuture<RangeSlice<DatasetsSummary>> getDatasets(GetDatasetsParam param) {
        return CompletableFuture.supplyAsync(() -> client.getDatasets(param));
    }

    /**
     * Datasetを取得する。(GET /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @return 取得結果のCompletableFuture
     * @see DsmoqClient#getDataset(String)
     */
    public CompletableFuture<Dataset> getDataset(String datasetId) {
        return CompletableFuture.supplyAsync(() -> client.getDataset(datasetId));
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDatasetのCompletableFuture
     * @see DsmoqClient#createDataset(boolean, boolean, File...)
     */
    public CompletableFuture<Dataset> createDataset(boolean saveLocal, boolean saveS3, File... files) {
        return CompletableFuture.supplyAsync(() -> client.createDataset(saveLocal, saveS3, files));
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @return 作成したDatasetのCompletableFuture
     * @see DsmoqClient#createDataset(String, boolean, boolean)
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
     * @return 作成したDatasetのCompletableFuture
     * @see DsmoqClient#createDataset(String, boolean, boolean, File...)
     */
    public CompletableFuture<Dataset> createDataset(String name, boolean saveLocal, boolean saveS3, File... files) {
        return CompletableFuture.supplyAsync(() -> client.createDataset(name, saveLocal, saveS3, files));
    }

    /**
     * Datasetにファイルを追加する。(POST /api/datasets/${dataset_id}/files相当)
     * @param datasetId DatasetID
     * @param files Datasetに追加するファイル(複数可)
     * @return 追加したファイルの情報のCompletableFuture
     * @see DsmoqClient#addFiles(String, File...)
     */
    public CompletableFuture<DatasetAddFiles> addFiles(String datasetId, File... files) {
        return CompletableFuture.supplyAsync(() -> client.addFiles(datasetId, files));
    }

    /**
     * ファイルを更新する。(POST /api/datasets/${dataset_id}/files/${file_id}相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param file 更新対象のファイル
     * @return 更新されたファイル情報のCompletableFuture
     * @see DsmoqClient#updateFile(String, String, File...)
     */
    public CompletableFuture<DatasetFile> updateFile(String datasetId, String fileId, File file) {
        return CompletableFuture.supplyAsync(() -> client.updateFile(datasetId, fileId, file));
    }

    /**
     * ファイル情報を更新する。(POST /api/datasets/${dataset_id}/files/${file_id}/metadata相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param param ファイル更新情報
     * @return 更新したファイル情報のCompletableFuture
     * @see DsmoqClient#updateFileMetaInfo(String, String, UpdateFileMetaParam)
     */
    public CompletableFuture<DatasetFile> updateFileMetaInfo(String datasetId, String fileId, UpdateFileMetaParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateFileMetaInfo(datasetId, fileId, param));
    }

    /**
     * データセットからファイルを削除する。（DELETE /api/datasets/${dataset_id}/files/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#deleteFile(String, String)
     */
    public CompletableFuture<Void> deleteFile(String datasetId, String fileId) {
        return CompletableFuture.runAsync(() -> client.deleteFile(datasetId, fileId));
    }

    /**
     * データセットの情報を更新する。(PUT /api/datasets.${dataset_id}/metadata相当)
     * @param datasetId DatasetID
     * @param param データセット更新情報
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#updateDatasetMetaInfo(String, UpdateDatasetMetaParam)
     */
    public CompletableFuture<Void> updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        return CompletableFuture.runAsync(() -> client.updateDatasetMetaInfo(datasetId, param));
    }

    /**
     * データセットに画像を追加する。（POST /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param files 追加する画像ファイル
     * @return 追加した画像情報のCompletableFuture
     * @see DsmoqClient#addImagesToDataset(String, File...)
     */
    public CompletableFuture<DatasetAddImages> addImagesToDataset(String datasetId, File... files) {
        return CompletableFuture.supplyAsync(() -> client.addImagesToDataset(datasetId, files));
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。（PUT /api/datasets/${dataset_id}/image/primary相当）
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#setPrimaryImageToDataset(String, SetPrimaryImageParam)
     */
    public CompletableFuture<Void> setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToDataset(datasetId, param));
    }


    /**
     * データセットに一覧で表示するメイン画像を設定する。
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#setPrimaryImageToDataset(String, File)
     */
    public CompletableFuture<Void> setPrimaryImageToDataset(String datasetId, File file) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToDataset(datasetId, file));
    }

    /**
     * データセットから画像を削除する。（DELETE /api/datasets/${dataset_id}/image/${image_id}相当）
     * @param datasetId DatasetID
     * @param imageId 画像ID
     * @return 画像削除後のデータセットのメイン画像情報のCompletableFuture
     * @see DsmoqClient#deleteImageToDataset(String, String)
     */
    public CompletableFuture<DatasetDeleteImage> deleteImageToDataset(String datasetId, String imageId) {
        return CompletableFuture.supplyAsync(() -> client.deleteImageToDataset(datasetId, imageId));
    }

    /**
     * データセットの画像一覧を取得する。（GET /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットの画像一覧のCompletableFuture
     * @see DsmoqClient#getDatasetImage(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetGetImage>> getDatasetImage(String datasetId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getDatasetImage(datasetId, param));
    }

    /**
     * データセットのアクセス権一覧を取得する。（GET /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのアクセス権一覧のCompletableFuture
     * @see DsmoqClient#getAccessLevel(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetOwnership>> getAccessLevel(String datasetId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getAccessLevel(datasetId, param));
    }

    /**
     * データセットのアクセス権を変更する。（POST /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param params アクセス権制御情報
     * @return 変更後のアクセス権情報のCompletableFuture
     * @see DsmoqClient#changeAccessLevel(String, List<SetAccessLevelParam>)
     */
    public CompletableFuture<DatasetOwnerships> changeAccessLevel(String datasetId, List<SetAccessLevelParam> params) {
        return CompletableFuture.supplyAsync(() -> client.changeAccessLevel(datasetId, params));
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。（PUT /api/datasets/${dataset_id}/guest_access相当）
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#changeGuestAccessLevel(String, SetGuestAccessLevelParam)
     */
    public CompletableFuture<Void> changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        return CompletableFuture.runAsync(() -> client.changeGuestAccessLevel(datasetId, param));
    }

    /**
     * データセットを削除する。(DELETE /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#deleteDataset(String)
     */
    public CompletableFuture<Void> deleteDataset(String datasetId) {
        return CompletableFuture.runAsync(() -> client.deleteDataset(datasetId));
    }

    /**
     * データセットの保存先を変更する。(PUT /api/datasets/${dataset_id}/storage相当)
     * @param datasetId DatasetID
     * @param param 保存先変更情報
     * @return 変更タスクの情報のCompletableFuture
     * @see DsmoqClient#changeDatasetStorage(String, ChangeStorageParam)
     */
    public CompletableFuture<DatasetTask> changeDatasetStorage(String datasetId, ChangeStorageParam param) {
        return CompletableFuture.supplyAsync(() -> client.changeDatasetStorage(datasetId, param));
    }

    /**
     * データセットをコピーします。（POST /api/datasets/${dataset_id}/copy相当）
     * @param datasetId DatasetID
     * @return コピーしたDatasetIDのCompletableFuture
     * @see DsmoqClient#copyDataset(String)
     */
    public CompletableFuture<String> copyDataset(String datasetId) {
        return CompletableFuture.supplyAsync(() -> client.copyDataset(datasetId));
    }

    /**
     * CSVファイルからAttributeを読み込む。（POST /api/datasets/${dataset_id}/attributes/import相当）
     * @param datasetId DatasetID
     * @param file AttributeをインポートするCSVファイル
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#importAttribute(String, File)
     */
    public CompletableFuture<Void> importAttribute(String datasetId, File file) {
        return CompletableFuture.runAsync(() -> client.importAttribute(datasetId, file));
    }

    /**
     * CSV形式のAttributeを取得する。（GET /api/datasets/${dataset_id}/attributes/export相当）
     * @param <T> CSVデータ処理後の型
     * @param datasetId DatasetID
     * @param f CSVデータを処理する関数 (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return fの処理結果
     * @see DsmoqClient#exportAttribute(String, Function<DatasetFileContent, T>)
     */
    public <T> CompletableFuture<T> exportAttribute(String datasetId, Function<DatasetFileContent, T> f) {
        return CompletableFuture.supplyAsync(() -> client.exportAttribute(datasetId, f));
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。（PUT /api/datasets/${dataset_id}/image/${image_id}/featured相当）
     * @param datasetId DatasetID
     * @param imageId 指定する画像ID
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#setFeaturedImageToDataset(String, String)
     */
    public CompletableFuture<Void> setFeaturedImageToDataset(String datasetId, String imageId) {
        return CompletableFuture.runAsync(() -> client.setFeaturedImageToDataset(datasetId, imageId));
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#setFeaturedImageToDataset(String, File)
     */
    public CompletableFuture<Void> setFeaturedImageToDataset(String datasetId, File file) {
        return CompletableFuture.runAsync(() -> client.setFeaturedImageToDataset(datasetId, file));
    }

    /**
     * データセットのファイルのファイルサイズを取得する。(HEAD /files/${dataset_id}/${file_id}相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return データセットのファイルのサイズのCompletableFuture
     * @see DsmoqClient#getFileSize(String, String)
     */
    public CompletableFuture<Long> getFileSize(String datasetId, String fileId) {
        return CompletableFuture.supplyAsync(() -> client.getFileSize(datasetId, fileId));
    }

    /**
     * データセットからファイルをダウンロードする。（GET /files/${dataset_id}/${file_id}相当）
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param f ファイルデータを処理する関数 (引数のDatasetFileはこの処理関数中でのみ利用可能)
     * @return fの処理結果のCompletableFuture
     * @see DsmoqClient#downloadFilet(String, String, Function<DatasetFileContent, T>)
     */
    public <T> CompletableFuture<T> downloadFile(String datasetId, String fileId, Function<DatasetFileContent, T> f) {
        return CompletableFuture.supplyAsync(() -> client.downloadFile(datasetId, fileId, f));
    }

    /**
     * データセットからファイルの内容を部分的に取得する。（GET /files/${dataset_id}/${file_id}相当）
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param from 開始位置指定、指定しない場合null
     * @param to 終了位置指定、指定しない場合null
     * @param f ファイルデータを処理する関数 (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return fの処理結果のCompletableFuture
     * @see DsmoqClient#downloadFileWithRange(String, String, Long, Long, Function<DatasetFileContent, T>)
     */
    public <T> CompletableFuture<T> downloadFileWithRange(String datasetId, String fileId, Long from, Long to, Function<DatasetFileContent, T> f) {
        return CompletableFuture.supplyAsync(() -> client.downloadFileWithRange(datasetId, fileId, from, to, f));
    }

    /**
     * グループ一覧を取得する。（GET /api/groups相当）
     * @param param グループ一覧取得情報
     * @return グループ一覧情報のCompletableFuture
     * @see DsmoqClient#downloadFileWithRange(String, String, Long, Long, Function<DatasetFileContent, T>)
     */
    public CompletableFuture<RangeSlice<GroupsSummary>> getGroups(GetGroupsParam param) {
        return CompletableFuture.supplyAsync(() -> client.getGroups(param));
    }

    /**
     * データセットのファイル一覧を取得する。（GET /api/datasets/${dataset_id}/files相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのファイル一覧のCompletableFuture
     * @see DsmoqClient#getDatasetFiles(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetFile>> getDatasetFiles(String datasetId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getDatasetFiles(datasetId, param));
    }

    /**
     * データセットのZIPファイルに含まれるファイル一覧を取得する。（GET /api/datasets/${dataset_id}/files/${fileId}/zippedfiles相当）
     * @param datasetId DatasetID
     * @param fileId FileID
     * @param param 一覧取得情報
     * @return ZIPファイル中のファイル一覧のCompletableFuture
     * @see DsmoqClient#getDatasetZippedFiles(String, String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetZipedFile>> getDatasetZippedFiles(String datasetId, String fileId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getDatasetZippedFiles(datasetId, fileId, param));
    }

    /**
     * グループ詳細を取得する。（GET /api/groups/${group_id}相当）
     * @param groupId グループID
     * @return グループ詳細情報のCompletableFuture
     * @see DsmoqClient#getGroup(String)
     */
    public CompletableFuture<Group> getGroup(String groupId) {
        return CompletableFuture.supplyAsync(() -> client.getGroup(groupId));
    }

    /**
     * グループのメンバー一覧を取得する。（GET /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param グループメンバー一覧取得情報
     * @return グループメンバー一覧情報のCompletableFuture
     * @see DsmoqClient#getMembers(String, GetMembersParam)
     */
    public CompletableFuture<RangeSlice<MemberSummary>> getMembers(String groupId, GetMembersParam param) {
        return CompletableFuture.supplyAsync(() -> client.getMembers(groupId, param));
    }

    /**
     * グループを作成する。（POST /api/groups相当）
     * @param param グループ作成情報
     * @return 作成したグループ詳細情報のCompletableFuture
     * @see DsmoqClient#createGroup(CreateGroupParam)
     */
    public CompletableFuture<Group> createGroup(CreateGroupParam param) {
        return CompletableFuture.supplyAsync(() -> client.createGroup(param));
    }

    /**
     * グループ詳細情報を更新する。（PUT /api/groups/${group_id}相当）
     * @param groupId グループID
     * @param param グループ詳細更新情報
     * @return グループ詳細情報のCompletableFuture
     * @see DsmoqClient#updateGroup(String, UpdateGroupParam)
     */
    public CompletableFuture<Group> updateGroup(String groupId, UpdateGroupParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateGroup(groupId, param));
    }

    /**
     * グループの画像一覧を取得する。（GET /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param param 一覧取得情報
     * @return グループの画像一覧情報のCompletableFuture
     * @see DsmoqClient#getGroupImage(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<GroupGetImage>> getGroupImage(String groupId, GetRangeParam param) {
        return CompletableFuture.supplyAsync(() -> client.getGroupImage(groupId, param));
    }

    /**
     * グループに画像を追加する。（POST /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param files 画像ファイル
     * @return 追加した画像ファイル情報のCompletableFuture
     * @see DsmoqClient#addImagesToGroup(String, File...)
     */
    public CompletableFuture<GroupAddImages> addImagesToGroup(String groupId, File... files) {
        return CompletableFuture.supplyAsync(() -> client.addImagesToGroup(groupId, files));
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。（PUT /api/groups/${group_id}/images/primary相当）
     * @param groupId グループID
     * @param param メイン画像指定情報
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#setPrimaryImageToGroup(String, SetPrimaryImageParam)
     */
    public CompletableFuture<Void> setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToGroup(groupId, param));
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     * @param groupId グループID
     * @param file 画像ファイル
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#setPrimaryImageToGroup(String, File)
     */
    public CompletableFuture<Void> setPrimaryImageToGroup(String groupId, File file) {
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToGroup(groupId, file));
    }

    /**
     * グループから画像を削除する。（DELETE /api/groups/${group_id}/images/${image_id}相当）
     * @param groupId グループID
     * @param imageId 画像ID
     * @return 画像削除後のグループのメイン画像情報のCompletableFuture
     * @see DsmoqClient#deleteImageToGroup(String, String)
     */
    public CompletableFuture<GroupDeleteImage> deleteImageToGroup(String groupId, String imageId) {
        return CompletableFuture.supplyAsync(() -> client.deleteImageToGroup(groupId, imageId));
    }

    /**
     * グループにメンバーを追加する。（POST /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param メンバー追加情報
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#addMember(String, List<AddMemberParam>)
     */
    public CompletableFuture<Void> addMember(String groupId, List<AddMemberParam> param) {
        return CompletableFuture.runAsync(() -> client.addMember(groupId, param));
    }

    /**
     * メンバーのロールを設定する。（PUT /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     * @param param ロール設定情報
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#setMemberRole(String, String, SetMemberRoleParam)
     */
    public CompletableFuture<Void> setMemberRole(String groupId, String userId, SetMemberRoleParam param) {
        return CompletableFuture.runAsync(() -> client.setMemberRole(groupId, userId, param));
    }

    /**
     * メンバーを削除する。（DELETE /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#deleteMember(String, String)
     */
    public CompletableFuture<Void> deleteMember(String groupId, String userId) {
        return CompletableFuture.runAsync(() -> client.deleteMember(groupId, userId));
    }

    /**
     * グループを削除する。（DELETE /api/groups/${group_id}相当）
     * @param groupId グループID
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#deleteGroup(String)
     */
    public CompletableFuture<Void> deleteGroup(String groupId) {
        return CompletableFuture.runAsync(() -> client.deleteGroup(groupId));
    }

    /**
     * ログインユーザのプロファイルを取得する。（GET /api/profile相当）
     * @return プロファイルのCompletableFuture
     * @see DsmoqClient#getProfile()
     */
    public CompletableFuture<User> getProfile() {
        return CompletableFuture.supplyAsync(() -> client.getProfile());
    }

    /**
     * ログインユーザのプロファイルを更新する。（PUT /api/profile相当）
     * @param param プロファイル更新情報
     * @return プロファイルのCompletableFuture
     * @see DsmoqClient#updateProfile(UpdateProfileParam)
     */
    public CompletableFuture<User> updateProfile(UpdateProfileParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateProfile(param));
    }

    /**
     * ログインユーザの画像を更新する。（POST /api/profile/image相当）
     * @param file 画像ファイル
     * @return プロファイルのCompletableFuture
     * @see DsmoqClient#updateProfileIcon(File)
     */
    public CompletableFuture<User> updateProfileIcon(File file) {
        return CompletableFuture.supplyAsync(() -> client.updateProfileIcon(file));
    }

    /**
     * ログインユーザのE-Mailを変更する。（POST /api/profile/email_change_request相当）
     * @param param E-Mail変更情報
     * @return プロファイルのCompletableFuture
     * @see DsmoqClient#updateEmail(UpdateEmailParam)
     */
    public CompletableFuture<User> updateEmail(UpdateEmailParam param) {
        return CompletableFuture.supplyAsync(() -> client.updateEmail(param));
    }

    /**
     * ログインユーザのパスワードを変更する。（PUT /api/profile/password相当）
     * @param param パスワード変更情報
     * @return 実行結果のCompletableFuture
     * @see DsmoqClient#changePassword(ChangePasswordParam)
     */
    public CompletableFuture<Void> changePassword(ChangePasswordParam param) {
        return CompletableFuture.runAsync(() -> client.changePassword(param));
    }

    /**
     * ユーザー一覧を取得する。（GET /api/accounts相当）
     * @return ユーザー一覧のCompletableFuture
     * @see DsmoqClient#getAccounts()
     */
    public CompletableFuture<List<User>> getAccounts() {
        return CompletableFuture.supplyAsync(() -> client.getAccounts());
    }

    /**
     * ライセンス一覧を取得する。（GET /api/licenses相当）
     * @return ライセンス一覧情報のCompletableFuture
     * @see DsmoqClient#getLicenses()
     */
    public CompletableFuture<List<License>> getLicenses() {
        return CompletableFuture.supplyAsync(() -> client.getLicenses());
    }

    /**
     * タスクの現在のステータスを取得する。（GET /api/tasks/${task_id}相当）
     * @param taskId タスクID
     * @return タスクのステータス情報のCompletableFuture
     * @see DsmoqClient#getTaskStatus(String)
     */
    public CompletableFuture<TaskStatus> getTaskStatus(String taskId) {
        return CompletableFuture.supplyAsync(() -> client.getTaskStatus(taskId));
    }

    /**
     * 統計情報を取得します。（GET /api/statistics相当）
     * @param param 統計情報期間指定
     * @return 統計情報のCompletableFuture
     * @see DsmoqClient#getStatistics(StatisticsParam)
     */
    public CompletableFuture<List<StatisticsDetail>> getStatistics(StatisticsParam param) {
        return CompletableFuture.supplyAsync(() -> client.getStatistics(param));
    }
}
