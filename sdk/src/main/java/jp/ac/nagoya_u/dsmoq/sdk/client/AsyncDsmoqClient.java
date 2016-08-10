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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotEmpty;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotNull;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotNullAll;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireGreaterOrEqualOrNull;

/**
 * 非同期にdsmoq APIを叩くためのクライアントクラス
 *
 * 個々のWeb APIの仕様については、APIのドキュメントを参照してください。
 */
public class AsyncDsmoqClient {
    private static Marker LOG_MARKER = MarkerFactory.getMarker("SDK");
    private static Logger logger = LoggerFactory.getLogger(LOG_MARKER.toString());
    private static ResourceBundle resource = ResourceBundle.getBundle("message");

    private DsmoqClient client;

    /**
     * クライアントオブジェクトを生成する。
     * 
     * @param baseUrl 基準となるURL
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     */
    public AsyncDsmoqClient(String baseUrl, String apiKey, String secretKey) {
        this.client = DsmoqClient.create(baseUrl, apiKey, secretKey);
    }

    /**
     * Datasetにファイルを追加する。
     * 
     * POST /api/datasets/${dataset_id}/files に相当。
     * 
     * @param datasetId DatasetID
     * @param files Datasetに追加するファイル(複数可)
     * @return 追加したファイルの情報のCompletableFuture
     * @throws NullPointerException datasetId、files、あるいはfilesの要素のいずれかがnullの場合
     * @see DsmoqClient#addFiles(String, File...)
     */
    public CompletableFuture<DatasetAddFiles> addFiles(String datasetId, File... files) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#addFiles start : [datasetId] = {}, [file num] = {}", datasetId,
                (files == null) ? "null" : files.length);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#addFiles");
        requireNotNull(files, "at files in AsyncDsmoqClient#addFiles");
        requireNotNullAll(files, "at files[%d] in AsyncDsmoqClient#addFiles");
        return CompletableFuture.supplyAsync(() -> client.addFiles(datasetId, files));
    }

    /**
     * データセットに画像を追加する。
     * 
     * POST /api/datasets/${dataset_id}/image に相当。
     * 
     * @param datasetId DatasetID
     * @param files 追加する画像ファイル
     * @return 追加した画像情報のCompletableFuture
     * @throws NullPointerException datasetId、files、filesの要素のいずれかがnullの場合
     * @see DsmoqClient#addImagesToDataset(String, File...)
     */
    public CompletableFuture<DatasetAddImages> addImagesToDataset(String datasetId, File... files) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#addImagesToDataset start : [datasetId] = {}, [file num] = {}",
                datasetId, (files == null) ? "null" : files.length);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#addImagesToDataset");
        requireNotNull(files, "at files in AsyncDsmoqClient#addImagesToDataset");
        requireNotNullAll(files, "at files[%d] in AsyncDsmoqClient#addImagesToDataset");
        return CompletableFuture.supplyAsync(() -> client.addImagesToDataset(datasetId, files));
    }

    /**
     * グループに画像を追加する。
     *
     * POST /api/groups/${group_id}/images に相当。
     * 
     * @param groupId グループID
     * @param files 画像ファイル
     * @return 追加した画像ファイル情報のCompletableFuture
     * @throws NullPointerException groupId、files、filesの要素のいずれかがnullの場合
     * @see DsmoqClient#addImagesToGroup(String, File...)
     */
    public CompletableFuture<GroupAddImages> addImagesToGroup(String groupId, File... files) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#addImagesToGroup start : [groupId] = {}, [file num] = {}", groupId,
                (files == null) ? "null" : files.length);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClienAsyncDsmoqClientaddImagesToGroup");
        requireNotNull(files, "at files in AsyncDsmoqClient#addImagesToGroup");
        requireNotNullAll(files, "at files[%d] in AsyncDsmoqClient#addImagesToGroup");
        return CompletableFuture.supplyAsync(() -> client.addImagesToGroup(groupId, files));
    }

    /**
     * グループにメンバーを追加する。
     *
     * POST /api/groups/${group_id}/members に相当。
     * 
     * @param groupId グループID
     * @param param メンバー追加情報
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException groupId、params、paramsの要素のいずれかがnullの場合
     * @see DsmoqClient#addMember(String, List<AddMemberParam>)
     */
    public CompletableFuture<Void> addMember(String groupId, List<AddMemberParam> params) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#addMember start : [groupId] = {}, [params] = {}", groupId, params);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#addMember");
        requireNotNull(params, "at params in AsyncDsmoqClient#addMember");
        requireNotNullAll(params, "at params[%s] in AsyncDsmoqClient#addMember");
        return CompletableFuture.runAsync(() -> client.addMember(groupId, params));
    }

    /**
     * データセットのアクセス権を変更する。
     *
     * POST /api/datasets/${dataset_id}/acl に相当。
     * 
     * @param datasetId DatasetID
     * @param params アクセス権制御情報
     * @return 変更後のアクセス権情報のCompletableFuture
     * @throws NullPointerException datasetId、params、paramsの要素のいずれかがnullの場合
     * @see DsmoqClient#changeAccessLevel(String, List<SetAccessLevelParam>)
     */
    public CompletableFuture<DatasetOwnerships> changeAccessLevel(String datasetId, List<SetAccessLevelParam> params) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#changeAccessLevel start : [datasetId] = {}, [params] = {}",
                datasetId, params);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#changeAccessLevel");
        requireNotNull(params, "at params in AsyncDsmoqClient#changeAccessLevel");
        requireNotNullAll(params, "at params[%d] in AsyncDsmoqClient#changeAccessLevel");
        return CompletableFuture.supplyAsync(() -> client.changeAccessLevel(datasetId, params));
    }

    /**
     * データセットの保存先を変更する。
     *
     * PUT /api/datasets/${dataset_id}/storage に相当。
     * 
     * @param datasetId DatasetID
     * @param param 保存先変更情報
     * @return 変更タスクの情報のCompletableFuture
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @see DsmoqClient#changeDatasetStorage(String, ChangeStorageParam)
     */
    public CompletableFuture<DatasetTask> changeDatasetStorage(String datasetId, ChangeStorageParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#changeDatasetStorage start : [datasetId] = {}, [param] = {}",
                datasetId, param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#changeDatasetStorage");
        requireNotNull(param, "at param in AsyncDsmoqClient#changeDatasetStorage");
        return CompletableFuture.supplyAsync(() -> client.changeDatasetStorage(datasetId, param));
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。
     *
     * PUT /api/datasets/${dataset_id}/guest_access に相当。
     * 
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @see DsmoqClient#changeGuestAccessLevel(String, SetGuestAccessLevelParam)
     */
    public CompletableFuture<Void> changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#changeGuestAccessLevel start : [datasetId] = {}, [param] = {}",
                datasetId, param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#changeGuestAccessLevel");
        requireNotNull(param, "at param in AsyncDsmoqClient#changeGuestAccessLevel");
        return CompletableFuture.runAsync(() -> client.changeGuestAccessLevel(datasetId, param));
    }

    /**
     * ログインユーザのパスワードを変更する。
     *
     * PUT /api/profile/password に相当。
     * 
     * @param param パスワード変更情報
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException paramがnullの場合
     * @see DsmoqClient#changePassword(ChangePasswordParam)
     */
    public CompletableFuture<Void> changePassword(ChangePasswordParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#changePassword start : [param] = {}", param);
        requireNotNull(param, "at param in AsyncDsmoqClient#changePassword");
        return CompletableFuture.runAsync(() -> client.changePassword(param));
    }

    /**
     * データセットをコピーする。
     *
     * POST /api/datasets/${dataset_id}/copy に相当。
     * 
     * @param datasetId DatasetID
     * @return コピーしたDatasetIDのCompletableFuture
     * @throws NullPointerException datasetIdがnullの場合
     * @see DsmoqClient#copyDataset(String)
     */
    public CompletableFuture<String> copyDataset(String datasetId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#copyDataset start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#copyDataset");
        return CompletableFuture.supplyAsync(() -> client.copyDataset(datasetId));
    }

    /**
     * Datasetを作成する。
     *
     * POST /api/datasets に相当。
     * 作成されるDatasetの名前は、最初に指定されたファイル名となる。
     * 
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDatasetのCompletableFuture
     * @throws NullPointerException files、あるいはfilesの要素のいずれかがnullの場合
     * @throws NoSuchElementException filesの要素が存在しない場合
     * @see DsmoqClient#createDataset(boolean, boolean, File...)
     */
    public CompletableFuture<Dataset> createDataset(boolean saveLocal, boolean saveS3, File... files) {
        logger.debug(LOG_MARKER,
                "AsyncDsmoqClient#createDataset start : [saveLocal] = {}, [saveS3] = {}, [file num] = {}", saveLocal,
                saveS3, (files == null) ? null : files.length);
        requireNotNull(files, "at files in AsyncDsmoqClient#createDataset");
        requireNotEmpty(files, "at files in AsyncDsmoqClient#createDataset");
        requireNotNullAll(files, "at files[%d] in AsyncDsmoqClient#createDataset");
        return CompletableFuture.supplyAsync(() -> client.createDataset(saveLocal, saveS3, files));
    }

    /**
     * Datasetを作成する。
     *
     * POST /api/datasets に相当。
     * 
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @return 作成したDatasetのCompletableFuture
     * @throws NullPointerException nameがnullの場合
     * @see DsmoqClient#createDataset(String, boolean, boolean)
     */
    public CompletableFuture<Dataset> createDataset(String name, boolean saveLocal, boolean saveS3) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#createDataset start : [name] = {}, [saveLocal] = {}, [saveS3] = {}",
                name, saveLocal, saveS3);
        requireNotNull(name, "at name in AsyncDsmoqClient#createDataset");
        return CompletableFuture.supplyAsync(() -> client.createDataset(name, saveLocal, saveS3));
    }

    /**
     * Datasetを作成する。
     *
     * POST /api/datasets に相当。
     * 
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDatasetのCompletableFuture
     * @throws NullPointerException name、files、あるいはfilesの要素のいずれかがnullの場合
     * @see DsmoqClient#createDataset(String, boolean, boolean, File...)
     */
    public CompletableFuture<Dataset> createDataset(String name, boolean saveLocal, boolean saveS3, File... files) {
        logger.debug(LOG_MARKER,
                "AsyncDsmoqClient#createDataset start : [name] = {}, [saveLocal] = {}, [saveS3] = {}, [file num] = {}",
                name, saveLocal, saveS3, (files == null) ? "null" : files.length);
        requireNotNull(name, "at name in AsyncDsmoqClient#createDataset");
        requireNotNull(files, "at files in AsyncDsmoqClient#createDataset");
        requireNotNullAll(files, "at files[%d] in AsyncDsmoqClient#createDataset");
        return CompletableFuture.supplyAsync(() -> client.createDataset(name, saveLocal, saveS3, files));
    }

    /**
     * グループを作成する。
     *
     * POST /api/groups に相当。
     * 
     * @param param グループ作成情報
     * @return 作成したグループ詳細情報のCompletableFuture
     * @throws NullPointerException paramがnullの場合
     * @see DsmoqClient#createGroup(CreateGroupParam)
     */
    public CompletableFuture<Group> createGroup(CreateGroupParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#createGroup start : [param] = {}", param);
        requireNotNull(param, "at param in AsyncDsmoqClient#createGroup");
        return CompletableFuture.supplyAsync(() -> client.createGroup(param));
    }

    /**
     * データセットを削除する。
     *
     * DELETE /api/datasets/${dataset_id} に相当。
     * 
     * @param datasetId DatasetID
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetIdがnullの場合
     * @see DsmoqClient#deleteDataset(String)
     */
    public CompletableFuture<Void> deleteDataset(String datasetId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#deleteDataset start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#deleteDataset");
        return CompletableFuture.runAsync(() -> client.deleteDataset(datasetId));
    }

    /**
     * データセットからファイルを削除する。
     *
     * DELETE /api/datasets/${dataset_id}/files/${file_id} に相当。
     * 
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、fileIdのいずれかがnullの場合
     * @see DsmoqClient#deleteFile(String, String)
     */
    public CompletableFuture<Void> deleteFile(String datasetId, String fileId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#deleteFile start : [datasetId] = {}, [fileId] = {}", datasetId,
                fileId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#deleteFile");
        requireNotNull(fileId, "at fileId in AsyncDsmoqClient#deleteFile");
        return CompletableFuture.runAsync(() -> client.deleteFile(datasetId, fileId));
    }

    /**
     * グループを削除する。
     *
     * DELETE /api/groups/${group_id} に相当。
     * 
     * @param groupId グループID
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException groupIdがnullの場合
     * @see DsmoqClient#deleteGroup(String)
     */
    public CompletableFuture<Void> deleteGroup(String groupId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#deleteGroup start : [groupId] = {}", groupId);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#deleteGroup");
        return CompletableFuture.runAsync(() -> client.deleteGroup(groupId));
    }

    /**
     * データセットから画像を削除する。
     *
     * DELETE /api/datasets/${dataset_id}/image/${image_id} に相当。
     * 
     * @param datasetId DatasetID
     * @param imageId 画像ID
     * @return 画像削除後のデータセットのメイン画像情報のCompletableFuture
     * @throws NullPointerException datasetId、imageIdのいずれかがnullの場合
     * @see DsmoqClient#deleteImageToDataset(String, String)
     */
    public CompletableFuture<DatasetDeleteImage> deleteImageToDataset(String datasetId, String imageId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#deleteImageToDataset start : [datasetId] = {}, [imageId] = {}",
                datasetId, imageId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#deleteImageToDataset");
        requireNotNull(imageId, "at imageId in AsyncDsmoqClient#deleteImageToDataset");
        return CompletableFuture.supplyAsync(() -> client.deleteImageToDataset(datasetId, imageId));
    }

    /**
     * グループから画像を削除する。
     *
     * DELETE /api/groups/${group_id}/images/${image_id} に相当。
     * 
     * @param groupId グループID
     * @param imageId 画像ID
     * @return 画像削除後のグループのメイン画像情報のCompletableFuture
     * @throws NullPointerException groupId、imageIdのいずれかがnullの場合
     * @see DsmoqClient#deleteImageToGroup(String, String)
     */
    public CompletableFuture<GroupDeleteImage> deleteImageToGroup(String groupId, String imageId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#deleteImageToGroup start : [groupId] = {}, [imageId] = {}", groupId,
                imageId);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#deleteImageToGroup");
        requireNotNull(imageId, "at imageId in AsyncDsmoqClient#deleteImageToGroup");
        return CompletableFuture.supplyAsync(() -> client.deleteImageToGroup(groupId, imageId));
    }

    /**
     * メンバーを削除する。
     *
     * DELETE /api/groups/${group_id}/members/${user_id} に相当。
     * 
     * @param groupId グループID
     * @param userId ユーザーID
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException groupId、userIdのいずれかがnullの場合
     * @see DsmoqClient#deleteMember(String, String)
     */
    public CompletableFuture<Void> deleteMember(String groupId, String userId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#deleteMember start : [groupId] = {}, [userId] = {}", groupId,
                userId);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#deleteMember");
        requireNotNull(userId, "at userId in AsyncDsmoqClient#deleteMember");
        return CompletableFuture.runAsync(() -> client.deleteMember(groupId, userId));
    }

    /**
     * データセットからファイルをダウンロードする。
     *
     * GET /files/${dataset_id}/${file_id} に相当。
     * 
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param f ファイルデータを処理する関数 (引数のDatasetFileはこの処理関数中でのみ利用可能)
     * @return fの処理結果のCompletableFuture
     * @throws NullPointerException datasetIdまたはfileIdまたはfがnullの場合
     * @see DsmoqClient#downloadFilet(String, String,
     *      Function<DatasetFileContent, T>)
     */
    public <T> CompletableFuture<T> downloadFile(String datasetId, String fileId, Function<DatasetFileContent, T> f) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#downloadFile start : [datasetId] = {}, [fileId] = {}", datasetId,
                fileId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#downloadFile");
        requireNotNull(fileId, "at fileId in AsyncDsmoqClient#downloadFile");
        requireNotNull(f, "at f in AsyncDsmoqClient#downloadFile");
        return CompletableFuture.supplyAsync(() -> client.downloadFile(datasetId, fileId, f));
    }

    /**
     * データセットからファイルの内容を部分的に取得する。
     *
     * GET /files/${dataset_id}/${file_id} に相当。
     * 
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param from 開始位置指定、指定しない場合null
     * @param to 終了位置指定、指定しない場合null
     * @param f ファイルデータを処理する関数 (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return fの処理結果のCompletableFuture
     * @throws NullPointerException datasetIdまたはfileIdまたはfがnullの場合
     * @throws IllegalArgumentException fromまたはtoが0未満の場合
     * @see DsmoqClient#downloadFileWithRange(String, String, Long, Long,
     *      Function<DatasetFileContent, T>)
     */
    public <T> CompletableFuture<T> downloadFileWithRange(String datasetId, String fileId, Long from, Long to,
            Function<DatasetFileContent, T> f) {
        logger.debug(LOG_MARKER,
                "AsyncDsmoqClient#downloadFileWithRange start : [datasetId] = {}, [fileId] = {}, [from:to] = {}:{}",
                datasetId, fileId, from, to);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#downloadFileWithRange");
        requireNotNull(fileId, "at fileId in AsyncDsmoqClient#downloadFileWithRange");
        requireNotNull(f, "at f in AsyncDsmoqClient#downloadFileWithRange");
        requireGreaterOrEqualOrNull(from, 0L, "at from in AsyncDsmoqClient#downloadFileWithRange");
        requireGreaterOrEqualOrNull(to, 0L, "at to in AsyncDsmoqClient#downloadFileWithRange");
        return CompletableFuture.supplyAsync(() -> client.downloadFileWithRange(datasetId, fileId, from, to, f));
    }

    /**
     * CSV形式のAttributeを取得する。
     *
     * GET /api/datasets/${dataset_id}/attributes/export に相当。
     * 
     * @param <T> CSVデータ処理後の型
     * @param datasetId DatasetID
     * @param f CSVデータを処理する関数 (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return fの処理結果
     * @throws NullPointerException datasetIdまたはfがnullの場合
     * @see DsmoqClient#exportAttribute(String, Function<DatasetFileContent, T>)
     */
    public <T> CompletableFuture<T> exportAttribute(String datasetId, Function<DatasetFileContent, T> f) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#exportAttribute start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#exportAttribute");
        requireNotNull(f, "at f in AsyncDsmoqClient#exportAttribute");
        return CompletableFuture.supplyAsync(() -> client.exportAttribute(datasetId, f));
    }

    /**
     * データセットのアクセス権一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/acl に相当。
     * 
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのアクセス権一覧のCompletableFuture
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @see DsmoqClient#getAccessLevel(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetOwnership>> getAccessLevel(String datasetId, GetRangeParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getAccessLevel start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#getAccessLevel");
        requireNotNull(param, "at param in AsyncDsmoqClient#getAccessLevel");
        return CompletableFuture.supplyAsync(() -> client.getAccessLevel(datasetId, param));
    }

    /**
     * ユーザー一覧を取得する。
     *
     * GET /api/accounts に相当。
     * 
     * @return ユーザー一覧のCompletableFuture
     * @see DsmoqClient#getAccounts()
     */
    public CompletableFuture<List<User>> getAccounts() {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getAccounts start");
        return CompletableFuture.supplyAsync(() -> client.getAccounts());
    }

    /**
     * Datasetを取得する。
     *
     * GET /api/datasets/${dataset_id} に相当。
     * 
     * @param datasetId DatasetID
     * @return 取得結果のCompletableFuture
     * @throws NullPointerException datasetIdがnullの場合
     * @see DsmoqClient#getDataset(String)
     */
    public CompletableFuture<Dataset> getDataset(String datasetId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getDataset start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#getDataset");
        return CompletableFuture.supplyAsync(() -> client.getDataset(datasetId));
    }

    /**
     * データセットのファイル一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/files に相当。
     * 
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのファイル一覧のCompletableFuture
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @see DsmoqClient#getDatasetFiles(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetFile>> getDatasetFiles(String datasetId, GetRangeParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getDatasetFiles start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#getDatasetFiles");
        requireNotNull(param, "at param in AsyncDsmoqClient#getDatasetFiles");
        return CompletableFuture.supplyAsync(() -> client.getDatasetFiles(datasetId, param));
    }

    /**
     * データセットの画像一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/image に相当。
     * 
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットの画像一覧のCompletableFuture
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @see DsmoqClient#getDatasetImage(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetGetImage>> getDatasetImage(String datasetId, GetRangeParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getDatasetImage start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#getDatasetImage");
        requireNotNull(param, "at param in AsyncDsmoqClient#getDatasetImage");
        return CompletableFuture.supplyAsync(() -> client.getDatasetImage(datasetId, param));
    }

    /**
     * Datasetを検索する。
     *
     * GET /api/datasets に相当。
     * 
     * @param param Dataset検索に使用するパラメタ
     * @return 検索結果のCompletableFuture
     * @throws NullPointerException paramsがnullの場合
     * @see DsmoqClient#getDatasets(GetDatasetsParam)
     */
    public CompletableFuture<RangeSlice<DatasetsSummary>> getDatasets(GetDatasetsParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getDatasets start : [param] = {}", param);
        requireNotNull(param, "at param in AsyncDsmoqClient#getDatasets");
        return CompletableFuture.supplyAsync(() -> client.getDatasets(param));
    }

    /**
     * データセットのZIPファイルに含まれるファイル一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/files/${fileId}/zippedfiles に相当。
     * 
     * @param datasetId DatasetID
     * @param fileId FileID
     * @param param 一覧取得情報
     * @return ZIPファイル中のファイル一覧のCompletableFuture
     * @throws NullPointerException datasetId、fileId、paramのいずれかがnullの場合
     * @see DsmoqClient#getDatasetZippedFiles(String, String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<DatasetZipedFile>> getDatasetZippedFiles(String datasetId, String fileId,
            GetRangeParam param) {
        logger.debug(LOG_MARKER,
                "AsyncDsmoqClient#getDatasetZippedFiles start : [datasetId] = {}, [fileId] = {}, [param] = {}",
                datasetId, fileId, param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#getDatasetZippedFiles");
        requireNotNull(fileId, "at fileId in AsyncDsmoqClient#getDatasetZippedFiles");
        requireNotNull(param, "at param in AsyncDsmoqClient#getDatasetZippedFiles");
        return CompletableFuture.supplyAsync(() -> client.getDatasetZippedFiles(datasetId, fileId, param));
    }

    /**
     * データセットに設定されているファイルのサイズを取得する。
     *
     * HEAD /files/${dataset_id}/${file_id} に相当。
     * 
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return データセットのファイルのサイズのCompletableFuture
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @see DsmoqClient#getFileSize(String, String)
     */
    public CompletableFuture<Long> getFileSize(String datasetId, String fileId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getFileSize start : [datasetId] = {}, [fileId] = {}", datasetId,
                fileId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#getFileSize");
        requireNotNull(fileId, "at fileId in AsyncDsmoqClient#getFileSize");
        return CompletableFuture.supplyAsync(() -> client.getFileSize(datasetId, fileId));
    }

    /**
     * グループ詳細を取得する。
     *
     * GET /api/groups/${group_id} に相当。
     * 
     * @param groupId グループID
     * @return グループ詳細情報のCompletableFuture
     * @throws NullPointerException groupIdがnullの場合
     * @see DsmoqClient#getGroup(String)
     */
    public CompletableFuture<Group> getGroup(String groupId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getGroup start : [groupId] = {}", groupId);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#getGroup");
        return CompletableFuture.supplyAsync(() -> client.getGroup(groupId));
    }

    /**
     * グループの画像一覧を取得する。
     *
     * GET /api/groups/${group_id}/images に相当。
     * 
     * @param groupId グループID
     * @param param 一覧取得情報
     * @return グループの画像一覧情報のCompletableFuture
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @see DsmoqClient#getGroupImage(String, GetRangeParam)
     */
    public CompletableFuture<RangeSlice<GroupGetImage>> getGroupImage(String groupId, GetRangeParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getGroupImage start : [groupId] = {}, [param] = {}", groupId, param);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#getGroupImage");
        requireNotNull(param, "at param in AsyncDsmoqClient#getGroupImage");
        return CompletableFuture.supplyAsync(() -> client.getGroupImage(groupId, param));
    }

    /**
     * グループ一覧を取得する。
     *
     * GET /api/groups に相当。
     * 
     * @param param グループ一覧取得情報
     * @return グループ一覧情報のCompletableFuture
     * @throws NullPointerException paramがnullの場合
     * @see DsmoqClient#downloadFileWithRange(String, String, Long, Long,
     *      Function<DatasetFileContent, T>)
     */
    public CompletableFuture<RangeSlice<GroupsSummary>> getGroups(GetGroupsParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getGroups start : [param] = {}", param);
        requireNotNull(param, "at param in AsyncDsmoqClient#getGroups");
        return CompletableFuture.supplyAsync(() -> client.getGroups(param));
    }

    /**
     * ライセンス一覧を取得する。
     *
     * GET /api/licenses に相当。
     * 
     * @return ライセンス一覧情報のCompletableFuture
     * @see DsmoqClient#getLicenses()
     */
    public CompletableFuture<List<License>> getLicenses() {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getLicenses start");
        return CompletableFuture.supplyAsync(() -> client.getLicenses());
    }

    /**
     * グループのメンバー一覧を取得する。
     *
     * GET /api/groups/${group_id}/members に相当。
     * 
     * @param groupId グループID
     * @param param グループメンバー一覧取得情報
     * @return グループメンバー一覧情報のCompletableFuture
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @see DsmoqClient#getMembers(String, GetMembersParam)
     */
    public CompletableFuture<RangeSlice<MemberSummary>> getMembers(String groupId, GetMembersParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getMembers start : [groupId] = {}, [param] = {}", groupId, param);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#getMembers");
        requireNotNull(param, "at param in AsyncDsmoqClient#getMembers");
        return CompletableFuture.supplyAsync(() -> client.getMembers(groupId, param));
    }

    /**
     * ログインユーザのプロファイルを取得する。
     *
     * GET /api/profile に相当。
     * 
     * @return プロファイルのCompletableFuture
     * @see DsmoqClient#getProfile()
     */
    public CompletableFuture<User> getProfile() {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getProfile start");
        return CompletableFuture.supplyAsync(() -> client.getProfile());
    }

    /**
     * 統計情報を取得します。
     *
     * GET /api/statistics に相当。
     * 
     * @param param 統計情報期間指定
     * @return 統計情報のCompletableFuture
     * @throws NullPointerException paramがnullの場合
     * @see DsmoqClient#getStatistics(StatisticsParam)
     */
    public CompletableFuture<List<StatisticsDetail>> getStatistics(StatisticsParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getStatistics start : [param] = {}", param);
        requireNotNull(param, "at param in AsyncDsmoqClient#getStatistics");
        return CompletableFuture.supplyAsync(() -> client.getStatistics(param));
    }

    /**
     * タスクの現在のステータスを取得する。
     *
     * GET /api/tasks/${task_id} に相当。
     * 
     * @param taskId タスクID
     * @return タスクのステータス情報のCompletableFuture
     * @throws NullPointerException taskIdがnullの場合
     * @see DsmoqClient#getTaskStatus(String)
     */
    public CompletableFuture<TaskStatus> getTaskStatus(String taskId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#getTaskStatus start : [taskId] = {}", taskId);
        requireNotNull(taskId, "at taskId in AsyncDsmoqClient#getTaskStatus");
        return CompletableFuture.supplyAsync(() -> client.getTaskStatus(taskId));
    }

    /**
     * CSVファイルからAttributeを読み込む。
     *
     * POST /api/datasets/${dataset_id}/attributes/import に相当。
     * 
     * @param datasetId DatasetID
     * @param file AttributeをインポートするCSVファイル
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @see DsmoqClient#importAttribute(String, File)
     */
    public CompletableFuture<Void> importAttribute(String datasetId, File file) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#importAttribute start : [datasetId] = {}, [file] = {}", datasetId,
                (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#importAttribute");
        requireNotNull(file, "at file in AsyncDsmoqClient#importAttribute");
        return CompletableFuture.runAsync(() -> client.importAttribute(datasetId, file));
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。
     * 
     * PUT /api/datasets/${dataset_id}/images/featured に相当。
     * 
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @see DsmoqClient#setFeaturedImageToDataset(String, File)
     */
    public CompletableFuture<Void> setFeaturedImageToDataset(String datasetId, File file) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#setFeaturedImageToDataset start : [datasetId] = {}, [file] = {}",
                datasetId, (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#setFeaturedImageToDataset");
        requireNotNull(file, "at file in AsyncDsmoqClient#setFeaturedImageToDataset");
        return CompletableFuture.runAsync(() -> client.setFeaturedImageToDataset(datasetId, file));
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。
     *
     * PUT /api/datasets/${dataset_id}/images/featured に相当。
     * 
     * @param datasetId DatasetID
     * @param imageId 指定する画像ID
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、imageIdのいずれかがnullの場合
     * @see DsmoqClient#setFeaturedImageToDataset(String, String)
     */
    public CompletableFuture<Void> setFeaturedImageToDataset(String datasetId, String imageId) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#setFeaturedImageToDataset start : [datasetId] = {}, [imageId] = {}",
                datasetId, imageId);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#setFeaturedImageToDataset");
        requireNotNull(imageId, "at imageId in AsyncDsmoqClient#setFeaturedImageToDataset");
        return CompletableFuture.runAsync(() -> client.setFeaturedImageToDataset(datasetId, imageId));
    }

    /**
     * メンバーのロールを設定する。
     *
     * PUT /api/groups/${group_id}/members/${user_id} に相当。
     * 
     * @param groupId グループID
     * @param userId ユーザーID
     * @param param ロール設定情報
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException groupId、userId、paramのいずれかがnullの場合
     * @see DsmoqClient#setMemberRole(String, String, SetMemberRoleParam)
     */
    public CompletableFuture<Void> setMemberRole(String groupId, String userId, SetMemberRoleParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#setMemberRole start : [groupId] = {}, [userId] = {}, [param] = {}",
                groupId, userId, param);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#setMemberRole");
        requireNotNull(userId, "at userId in AsyncDsmoqClient#setMemberRole");
        requireNotNull(param, "at param in AsyncDsmoqClient#setMemberRole");
        return CompletableFuture.runAsync(() -> client.setMemberRole(groupId, userId, param));
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。
     * 
     * PUT /api/datasets/${dataset_id}/image/primary に相当。
     * 
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @see DsmoqClient#setPrimaryImageToDataset(String, File)
     */
    public CompletableFuture<Void> setPrimaryImageToDataset(String datasetId, File file) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#setPrimaryImageToDataset start : [datasetId] = {}, [file] = {}",
                datasetId, (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#setPrimaryImageToDataset");
        requireNotNull(file, "at file in AsyncDsmoqClient#setPrimaryImageToDataset");
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToDataset(datasetId, file));
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。
     *
     * PUT /api/datasets/${dataset_id}/image/primary に相当。
     * 
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @see DsmoqClient#setPrimaryImageToDataset(String, SetPrimaryImageParam)
     */
    public CompletableFuture<Void> setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#setPrimaryImageToDataset start : [datasetId] = {}, [param] = {}",
                datasetId, param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#setPrimaryImageToDataset");
        requireNotNull(param, "at param in AsyncDsmoqClient#setPrimaryImageToDataset");
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToDataset(datasetId, param));
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     * 
     * PUT /api/groups/${group_id}/images/primary に相当。
     * 
     * @param groupId グループID
     * @param file 画像ファイル
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException groupId、fileのいずれかがnullの場合
     * @see DsmoqClient#setPrimaryImageToGroup(String, File)
     */
    public CompletableFuture<Void> setPrimaryImageToGroup(String groupId, File file) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#setPrimaryImageToGroup start : [groupId] = {}, [file] = {}", groupId,
                (file == null) ? "null" : file.getName());
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#setPrimaryImageToGroup");
        requireNotNull(file, "at file in AsyncDsmoqClient#setPrimaryImageToGroup");
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToGroup(groupId, file));
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     *
     * PUT /api/groups/${group_id}/images/primary に相当。
     * 
     * @param groupId グループID
     * @param param メイン画像指定情報
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @see DsmoqClient#setPrimaryImageToGroup(String, SetPrimaryImageParam)
     */
    public CompletableFuture<Void> setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#setPrimaryImageToGroup start : [groupId] = {}, [param] = {}",
                groupId, param);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#setPrimaryImageToGroup");
        requireNotNull(param, "at param in AsyncDsmoqClient#setPrimaryImageToGroup");
        return CompletableFuture.runAsync(() -> client.setPrimaryImageToGroup(groupId, param));
    }

    /**
     * データセットの情報を更新する。
     *
     * PUT /api/datasets/${dataset_id}/metadata に相当。
     * 
     * @param datasetId DatasetID
     * @param param データセット更新情報
     * @return 実行結果のCompletableFuture
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @see DsmoqClient#updateDatasetMetaInfo(String, UpdateDatasetMetaParam)
     */
    public CompletableFuture<Void> updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#updateDatasetMetaInfo start : [datasetId] = {}, [param] = {}",
                datasetId, param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#updateDatasetMetaInfo");
        requireNotNull(param, "at param in AsyncDsmoqClient#updateDatasetMetaInfo");
        return CompletableFuture.runAsync(() -> client.updateDatasetMetaInfo(datasetId, param));
    }

    /**
     * ログインユーザのE-Mailを変更する。
     *
     * POST /api/profile/email_change_request に相当。
     * 
     * @param param E-Mail変更情報
     * @return プロファイルのCompletableFuture
     * @throws NullPointerException paramがnullの場合
     * @see DsmoqClient#updateEmail(UpdateEmailParam)
     */
    public CompletableFuture<User> updateEmail(UpdateEmailParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#updateEmail start : [param] = {}", param);
        requireNotNull(param, "at param in AsyncDsmoqClient#updateEmail");
        return CompletableFuture.supplyAsync(() -> client.updateEmail(param));
    }

    /**
     * ファイルを更新する。
     *
     * POST /api/datasets/${dataset_id}/files/${file_id} に相当。
     * 
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param file 更新対象のファイル
     * @return 更新されたファイル情報のCompletableFuture
     * @throws NullPointerException datasetId、fileId、fileのいずれかがnullの場合
     * @see DsmoqClient#updateFile(String, String, File...)
     */
    public CompletableFuture<DatasetFile> updateFile(String datasetId, String fileId, File file) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#updateFile start : [datasetId] = {}, [fileId] = {}, [file] = {}",
                datasetId, fileId, (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#updateFile");
        requireNotNull(fileId, "at fileId in AsyncDsmoqClient#updateFile");
        requireNotNull(file, "at file in AsyncDsmoqClient#updateFile");
        return CompletableFuture.supplyAsync(() -> client.updateFile(datasetId, fileId, file));
    }

    /**
     * ファイル情報を更新する。
     *
     * POST /api/datasets/${dataset_id}/files/${file_id}/metadata に相当。
     * 
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param param ファイル更新情報
     * @return 更新したファイル情報のCompletableFuture
     * @throws NullPointerException datasetId、fileId、paramのいずれかがnullの場合
     * @see DsmoqClient#updateFileMetaInfo(String, String, UpdateFileMetaParam)
     */
    public CompletableFuture<DatasetFile> updateFileMetaInfo(String datasetId, String fileId,
            UpdateFileMetaParam param) {
        logger.debug(LOG_MARKER,
                "AsyncDsmoqClient#updateFileMetaInfo start : [datasetId] = {}, [fileId] = {}, [param] = {}", datasetId,
                fileId, param);
        requireNotNull(datasetId, "at datasetId in AsyncDsmoqClient#updateFileMetaInfo");
        requireNotNull(fileId, "at fileId in AsyncDsmoqClient#updateFileMetaInfo");
        requireNotNull(param, "at param in AsyncDsmoqClient#updateFileMetaInfo");
        return CompletableFuture.supplyAsync(() -> client.updateFileMetaInfo(datasetId, fileId, param));
    }

    /**
     * グループ詳細情報を更新する。
     *
     * PUT /api/groups/${group_id} に相当。
     * 
     * @param groupId グループID
     * @param param グループ詳細更新情報
     * @return グループ詳細情報のCompletableFuture
     * @see DsmoqClient#updateGroup(String, UpdateGroupParam)
     */
    public CompletableFuture<Group> updateGroup(String groupId, UpdateGroupParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#updateGroup start : [groupId] = {}, [param] = {}", groupId, param);
        requireNotNull(groupId, "at groupId in AsyncDsmoqClient#updateGroup");
        requireNotNull(param, "at param in AsyncDsmoqClient#updateGroup");
        return CompletableFuture.supplyAsync(() -> client.updateGroup(groupId, param));
    }

    /**
     * ログインユーザのプロファイルを更新する。
     *
     * PUT /api/profile に相当。
     * 
     * @param param プロファイル更新情報
     * @return プロファイルのCompletableFuture
     * @throws NullPointerException paramがnullの場合
     * @see DsmoqClient#updateProfile(UpdateProfileParam)
     */
    public CompletableFuture<User> updateProfile(UpdateProfileParam param) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#updateProfile start : [param] = {}", param);
        requireNotNull(param, "at param in AsyncDsmoqClient#updateProfile");
        return CompletableFuture.supplyAsync(() -> client.updateProfile(param));
    }

    /**
     * ログインユーザの画像を更新する。
     *
     * POST /api/profile/image に相当。
     * 
     * @param file 画像ファイル
     * @return プロファイルのCompletableFuture
     * @throws NullPointerException fileがnullの場合
     * @see DsmoqClient#updateProfileIcon(File)
     */
    public CompletableFuture<User> updateProfileIcon(File file) {
        logger.debug(LOG_MARKER, "AsyncDsmoqClient#updateProfileIcon start : [file] = {}",
                (file == null) ? "null" : file.getName());
        requireNotNull(file, "at file in AsyncDsmoqClient#updateProfileIcon");
        return CompletableFuture.supplyAsync(() -> client.updateProfileIcon(file));
    }
}
