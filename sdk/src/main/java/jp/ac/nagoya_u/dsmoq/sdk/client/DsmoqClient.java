package jp.ac.nagoya_u.dsmoq.sdk.client;

import jp.ac.nagoya_u.dsmoq.sdk.http.AutoCloseHttpClient;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpDelete;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpGet;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpHead;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpPost;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpPut;
import jp.ac.nagoya_u.dsmoq.sdk.request.AddMemberParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.ChangePasswordParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.ChangeStorageParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.CreateGroupParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetDatasetsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetGroupsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetMembersParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetAccessLevelParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetFeaturedImageToDatasetParam;
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
import jp.ac.nagoya_u.dsmoq.sdk.util.ApiFailedException;
import jp.ac.nagoya_u.dsmoq.sdk.util.ConnectionLostException;
import jp.ac.nagoya_u.dsmoq.sdk.util.ErrorRespondedException;
import jp.ac.nagoya_u.dsmoq.sdk.util.ExceptionSupplier;
import jp.ac.nagoya_u.dsmoq.sdk.util.HttpStatusException;
import jp.ac.nagoya_u.dsmoq.sdk.util.JsonUtil;
import jp.ac.nagoya_u.dsmoq.sdk.util.ResourceNames;
import jp.ac.nagoya_u.dsmoq.sdk.util.ResponseFunction;
import jp.ac.nagoya_u.dsmoq.sdk.util.TimeoutException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.charset.spi.CharsetProvider;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotEmpty;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotNull;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotNullAll;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireGreaterOrEqualOrNull;

/**
 * dsmoq APIを叩くためのクライアントクラス
 * 個々のWeb APIの仕様については、APIのドキュメントを参照してください。
 */
public class DsmoqClient {
    private static Marker LOG_MARKER = MarkerFactory.getMarker("SDK");
    private static Logger logger = LoggerFactory.getLogger(LOG_MARKER.toString());
    private static ResourceBundle resource = ResourceBundle.getBundle("message");

    private String _baseUrl;
    private String _apiKey;
    private String _secretKey;

    // ContentType="text/html; charset=utf-8"の定義
    private static final ContentType TEXT_PLAIN_UTF8 = ContentType.create("text/plain", StandardCharsets.UTF_8);

    /**
     * APIキー、シークレットキーを使用するクライアントオブジェクトを生成する。
     * @param baseUrl 基準となるURL
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     * @return 作成したクライアント
     */
    public static DsmoqClient create(String baseUrl, String apiKey, String secretKey) {
        return new DsmoqClient(baseUrl, apiKey, secretKey);
    }

    /**
     * クライアントオブジェクトを生成する。
     * @param baseUrl 基準となるURL
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     */
    private DsmoqClient(String baseUrl, String apiKey, String secretKey) {
        this._apiKey = apiKey;
        this._secretKey = secretKey;
        this._baseUrl = baseUrl;
    }

    /**
     * Datasetを検索する。(GET /api/datasets相当)
     * @param param Dataset検索に使用するパラメタ
     * @return 検索結果
     * @throws NullPointerException paramsがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<DatasetsSummary> getDatasets(GetDatasetsParam param) {
        requireNotNull(param, "at param in getDatasets");
        return get("/api/datasets", param.toJsonString(), JsonUtil::toDatasets);
    }

    /**
     * Datasetを取得する。(GET /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @return 取得結果
     * @throws NullPointerException datasetIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Dataset getDataset(String datasetId) {
        requireNotNull(datasetId, "at datasetId in getDataset");
        return get("/api/datasets/" + datasetId, JsonUtil::toDataset);
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * 作成されるDatasetの名前は、最初に指定されたファイル名となる。
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可、最低1要素必須)
     * @return 作成したDataset
     * @throws NullPointerException files、あるいはfilesの要素のいずれかがnullの場合
     * @throws NoSuchElementException filesの要素が存在しない場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Dataset createDataset(boolean saveLocal, boolean saveS3, File... files) {
        requireNotNull(files, "at files in createDataset");
        requireNotEmpty(files, "at files in createDataset");
        requireNotNullAll(files, "at files[%d] in createDataset");
        return createDataset(files[0].getName(), saveLocal, saveS3, files);
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @return 作成したDataset
     * @throws NullPointerException nameがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Dataset createDataset(String name, boolean saveLocal, boolean saveS3) {
        return createDataset(name, saveLocal, saveS3, new File[0]);
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDataset
     * @throws NullPointerException name、files、あるいはfilesの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Dataset createDataset(String name, boolean saveLocal, boolean saveS3, File... files) {
        requireNotNull(name, "at name in createDataset");
        requireNotNull(files, "at files in createDataset");
        requireNotNullAll(files, "at files[%d] in createDataset");
        logger.debug(LOG_MARKER, "createDataset start : [name] = {}, [saveLocal] = {}, [saveS3] = {}, [files size] = {}", name, saveLocal, saveS3, (files != null) ? files.length : "null");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // MultipartEntityBuilderのモード互換モードを設定
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            // MultipartEntityBuilderの文字コードにutf-8を設定
            builder.setCharset(StandardCharsets.UTF_8);
            // 送信データに"name"(データセットの名前)を追加(文字コードはutf-8と明示)
            builder.addTextBody("name", name, TEXT_PLAIN_UTF8);
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("file[]", file));
            builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
            builder.addTextBody("saveS3", saveS3 ? "true" : "false");
            return builder.build();
        };
//        logger.debug(LOG_MARKER, "createDataset end : receive json = {}", json);
        return post("/api/datasets", entity, JsonUtil::toDataset);
    }

    /**
     * Datasetにファイルを追加する。(POST /api/datasets/${dataset_id}/files相当)
     * @param datasetId DatasetID
     * @param files Datasetに追加するファイル(複数可)
     * @return 追加したファイルの情報
     * @throws NullPointerException datasetId、files、あるいはfilesの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public DatasetAddFiles addFiles(String datasetId, File... files) {
        requireNotNull(datasetId, "at datasetId in addFiles");
        requireNotNull(files, "at files in addFiles");
        requireNotNullAll(files, "at files[%d] in addFiles");
        logger.debug(LOG_MARKER, "addFiles start : [datasetId] = {}, [files size] = {}", datasetId, (files != null) ? files.length : "null");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // MultipartEntityBuilderのモード互換モードを設定
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            // MultipartEntityBuilderの文字コードにutf-8を設定
            builder.setCharset(StandardCharsets.UTF_8);
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("files", file));
            return builder.build();
        };
//        logger.debug(LOG_MARKER, "addFiles end : receive json = {}", json);
        return post("/api/datasets/" + datasetId + "/files", entity, JsonUtil::toDatasetAddFiles);
    }

    /**
     * ファイルを更新する。(POST /api/datasets/${dataset_id}/files/${file_id}相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param file 更新対象のファイル
     * @return 更新されたファイル情報
     * @throws NullPointerException datasetId、fileId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public DatasetFile updateFile(String datasetId, String fileId, File file) {
        requireNotNull(datasetId, "at datasetId in updateFile");
        requireNotNull(fileId, "at fileId in updateFile");
        requireNotNull(file, "at file in updateFile");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            return builder.build();
        };
        return post("/api/datasets/" + datasetId + "/files/" + fileId, entity, JsonUtil::toDatasetFile);
    }

    /**
     * ファイル情報を更新する。(POST /api/datasets/${dataset_id}/files/${file_id}/metadata相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param param ファイル更新情報
     * @return 更新したファイル情報
     * @throws NullPointerException datasetId、fileId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public DatasetFile updateFileMetaInfo(String datasetId, String fileId, UpdateFileMetaParam param) {
        requireNotNull(datasetId, "at datasetId in updateFileMetaInfo");
        requireNotNull(fileId, "at fileId in updateFileMetaInfo");
        requireNotNull(param, "at param in updateFileMetaInfo");
        return put("/api/datasets/" + datasetId + "/files/" + fileId + "/metadata", param.toJsonString(), JsonUtil::toDatasetFile);
    }

    /**
     * データセットからファイルを削除する。（DELETE /api/datasets/${dataset_id}/files/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @throws NullPointerException datasetId、fileIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteFile(String datasetId, String fileId) {
        requireNotNull(datasetId, "at datasetId in deleteFile");
        requireNotNull(fileId, "at fileId in deleteFile");
        delete("/api/datasets/" + datasetId + "/files/" + fileId, x -> x);
    }

    /**
     * データセットの情報を更新する。(PUT /api/datasets/${dataset_id}/metadata相当)
     * @param datasetId DatasetID
     * @param param データセット更新情報
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        requireNotNull(datasetId, "at datasetId in updateDatasetMetaInfo");
        requireNotNull(param, "at param in updateDatasetMetaInfo");
        put("/api/datasets/" + datasetId + "/metadata", param.toJsonString(), x -> x);
    }

    /**
     * データセットに画像を追加する。（POST /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param files 追加する画像ファイル
     * @return 追加した画像情報
     * @throws NullPointerException datasetId、files、filesの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public DatasetAddImages addImagesToDataset(String datasetId, File... files) {
        requireNotNull(datasetId, "at datasetId in addImageToDataset");
        requireNotNull(files, "at files in addImageToDataset");
        requireNotNullAll(files, "at files[%d] in addImageToDataset");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            return builder.build();
        };
        return post("/api/datasets/" + datasetId + "/images", entity, JsonUtil::toDatasetAddImages);
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。（PUT /api/datasets/${dataset_id}/image/primary相当）
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        requireNotNull(datasetId, "at datasetId in setPrimaryImageToDataset");
        requireNotNull(param, "at param in setPrimaryImageToDataset");
        put("/api/datasets/" + datasetId + "/images/primary", param.toJsonString(), x -> x);
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToDataset(String datasetId, File file) {
        requireNotNull(datasetId, "at datasetId in setPrimaryImageToDataset");
        requireNotNull(file, "at file in setPrimaryImageToDataset");
        DatasetAddImages image = addImagesToDataset(datasetId, file);
        setPrimaryImageToDataset(datasetId, new SetPrimaryImageParam(image.getImages().get(0).getId()));
    }

    /**
     * データセットから画像を削除する。（DELETE /api/datasets/${dataset_id}/image/${image_id}相当）
     * @param datasetId DatasetID
     * @param imageId 画像ID
     * @return 画像削除後のデータセットのメイン画像情報
     * @throws NullPointerException datasetId、imageIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public DatasetDeleteImage deleteImageToDataset(String datasetId, String imageId) {
        requireNotNull(datasetId, "at datasetId in deleteImageToDataset");
        requireNotNull(imageId, "at imageId in deleteImageToDataset");
        return delete("/api/datasets/" + datasetId + "/images/" + imageId, JsonUtil::toDatasetDeleteImage);
    }

    /**
     * データセットの画像一覧を取得する。（GET /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットの画像一覧
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<DatasetGetImage> getDatasetImage(String datasetId, GetRangeParam param) {
        requireNotNull(datasetId, "at datasetId in getDatasetImage");
        requireNotNull(param, "at param in getDatasetImage");
        return get("/api/datasets/" + datasetId + "/images", param.toJsonString(), JsonUtil::toDatasetGetImage);
    }

    /**
     * データセットのアクセス権一覧を取得する。（GET /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのアクセス権一覧
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<DatasetOwnership> getAccessLevel(String datasetId, GetRangeParam param) {
        requireNotNull(datasetId, "at datasetId in getAccessLevel");
        requireNotNull(param, "at param in getAccessLevel");
        return get("/api/datasets/" + datasetId + "/acl", param.toJsonString(), JsonUtil::toDatasetOwnership);
    }

    /**
     * データセットのアクセス権を変更する。（POST /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param params アクセス権制御情報
     * @return 変更後のアクセス権情報
     * @throws NullPointerException datasetId、params、paramsの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public DatasetOwnerships changeAccessLevel(String datasetId, List<SetAccessLevelParam> params) {
        requireNotNull(datasetId, "at datasetId in changeAccessLevel");
        requireNotNull(params, "at params in changeAccessLevel");
        requireNotNullAll(params, "at params[%d] in changeAccessLevel");
        return post("/api/datasets/" + datasetId + "/acl", SetAccessLevelParam.toJsonString(params), JsonUtil::toDatasetOwnerships);
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。（PUT /api/datasets/${dataset_id}/guest_access相当）
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        requireNotNull(datasetId, "at datasetId in changeGuestAccessLevel");
        requireNotNull(param, "at param in changeGuestAccessLevel");
        put("/api/datasets/" + datasetId + "/guest_access", param.toJsonString(), x -> x);
    }

    /**
     * データセットを削除する。(DELETE /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @throws NullPointerException datasetIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteDataset(String datasetId) {
        requireNotNull(datasetId, "at datasetId in deleteDataset");
        delete("/api/datasets/" + datasetId, x -> x);
    }

    /**
     * データセットの保存先を変更する。(PUT /api/datasets/${dataset_id}/storage相当)
     * @param datasetId DatasetID
     * @param param 保存先変更情報
     * @return 変更タスクの情報
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public DatasetTask changeDatasetStorage(String datasetId, ChangeStorageParam param) {
        requireNotNull(datasetId, "at datasetId in changeDatasetStorage");
        requireNotNull(param, "at param in changeDatasetStorage");
        return put("/api/datasets/" + datasetId  + "/storage", param.toJsonString(), JsonUtil::toDatasetTask);
    }

    /**
     * データセットをコピーします。（POST /api/datasets/${dataset_id}/copy相当）
     * @param datasetId DatasetID
     * @return コピーしたDatasetID
     * @throws NullPointerException datasetIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public String copyDataset(String datasetId) {
        requireNotNull(datasetId, "at datasetId in copyDataset");
        return post("/api/datasets/" + datasetId + "/copy", json -> JsonUtil.toCopiedDataset(json).getDatasetId());
    }

    /**
     * CSVファイルからAttributeを読み込む。（POST /api/datasets/${dataset_id}/attributes/import相当）
     * @param datasetId DatasetID
     * @param file AttributeをインポートするCSVファイル
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void importAttribute(String datasetId, File file) {
        requireNotNull(datasetId, "at datasetId in importAttribute");
        requireNotNull(file, "at file in importAttribute");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            return builder.build();
        };
        post("/api/datasets/" + datasetId + "/attributes/import", entity, x -> x);
    }

    /** exportAttributeの際に用いるファイル名 */
    private static String EXPORT_ATTRIBUTE_CSV_FILENAME = "export.csv";

    /**
     * CSV形式のAttributeを取得する。（GET /api/datasets/${dataset_id}/attributes/export相当）
     * @param <T> CSVデータ処理後の型
     * @param datasetId DatasetID
     * @param f CSVデータを処理する関数 (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return fの処理結果
     * @throws NullPointerException datasetIdまたはfがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public <T> T exportAttribute(String datasetId, Function<DatasetFileContent, T> f) {
        logger.debug(LOG_MARKER, "exportAttribute start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in exportAttribute");
        requireNotNull(f, "at f in exportAttribute");
        return get("/api/datasets/" + datasetId + "/attributes/export", (HttpResponse response) -> {
            return f.apply(new DatasetFileContent() {
                public String getName() {
                    return EXPORT_ATTRIBUTE_CSV_FILENAME;
                }
                public InputStream getContent() throws IOException {
                    return response.getEntity().getContent();
                }
                public void writeTo(OutputStream s) throws IOException {
                    response.getEntity().writeTo(s);
                }
            });
        });
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。（PUT /api/datasets/${dataset_id}/images/featured相当）
     * @param datasetId DatasetID
     * @param imageId 指定する画像ID
     * @throws NullPointerException datasetId、imageIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setFeaturedImageToDataset(String datasetId, String imageId) {
        requireNotNull(datasetId, "at datasetId in setFeaturedImageToDataset");
        requireNotNull(imageId, "at imageId in setFeaturedImageToDataset");
        put("/api/datasets/" + datasetId + "/images/featured", new SetFeaturedImageToDatasetParam(imageId).toJsonString(), x -> x);
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setFeaturedImageToDataset(String datasetId, File file) {
        requireNotNull(datasetId, "at datasetId in setFeaturedImageToDataset");
        requireNotNull(file, "at file in setFeaturedImageToDataset");
        DatasetAddImages image = addImagesToDataset(datasetId, file);
        setFeaturedImageToDataset(datasetId, image.getImages().get(0).getId());
    }

    /**
     * データセットに設定されているファイルのサイズを取得する。(HEAD /files/${dataset_id}/${file_id}相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return データセットに設定されているファイルのサイズ。取得できなかった場合、nullを返却する。
     * @throws NullPointerException datasetIdまたはfileIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Long getFileSize(String datasetId, String fileId) {
        requireNotNull(datasetId, "at datasetId in headFile");
        requireNotNull(fileId, "at fileId in headFile");
        return head("/files/" + datasetId + "/" + fileId, response -> {
            Header header = response.getFirstHeader("Content-Length");
            if (header == null) {
                logger.warn(LOG_MARKER, "Content-Length not found.");
                return null;
            }
            try {
                return Long.valueOf(header.getValue());
            } catch (NumberFormatException e) {
                logger.warn(LOG_MARKER, "Invalid Content-Length value. [value]:{}", header.getValue());
                return null;
            }
        });
    }

    /**
     * データセットからファイルをダウンロードする。（GET /files/${dataset_id}/${file_id}相当）
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param f ファイルデータを処理する関数 (引数のDatasetFileはこの処理関数中でのみ利用可能)
     * @return fの処理結果
     * @throws NullPointerException datasetIdまたはfileIdまたはfがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public <T> T downloadFile(String datasetId, String fileId, Function<DatasetFileContent, T> f) {
        return downloadFileWithRange(datasetId, fileId, null, null, f);
    }

    /**
     * データセットからファイルの内容を部分的に取得する。（GET /files/${dataset_id}/${file_id}相当）
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param from 開始位置指定、指定しない場合null
     * @param to 終了位置指定、指定しない場合null
     * @param f ファイルデータを処理する関数 (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return fの処理結果
     * @throws NullPointerException datasetIdまたはfileIdまたはfがnullの場合
     * @throws IllegalArgumentException fromまたはtoが0未満の場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public <T> T downloadFileWithRange(String datasetId, String fileId, Long from, Long to, Function<DatasetFileContent, T> f) {
        logger.debug(LOG_MARKER, "downloadFileWithRange start : [datasetId/fileId] = {}/{}, [from:to] = {}:{}", datasetId, fileId, from, to);
        requireNotNull(datasetId, "at datasetId in downloadFileWithRange");
        requireNotNull(fileId, "at fileId in downloadFileWithRange");
        requireNotNull(f, "at f in downloadFileWithRange");
        requireGreaterOrEqualOrNull(from, 0L, "at from in downloadFileWithRange");
        requireGreaterOrEqualOrNull(to, 0L, "at to in downloadFileWithRange");
        return get(
            "/files/" + datasetId + "/" + fileId,
            request -> {
                if (from != null || to != null) {
                    request.setHeader("Range", String.format("bytes=%s-%s", from == null ? "" : from.toString(), to == null ? "" : to.toString()));
                }
            },
            response -> {
                String filename = getFileNameFromHeader(response);
                return f.apply(new DatasetFileContent() {
                    public String getName() {
                        return filename;
                    }
                    public InputStream getContent() throws IOException {
                        return response.getEntity().getContent();
                    }
                    public void writeTo(OutputStream s) throws IOException {
                        response.getEntity().writeTo(s);
                    }
                });
            }
        );
    }

    /** HTTP Response の Content-Disposition ヘッダ */
    private static final String CONTENT_DISPOSITION_HEADER_NAME = "Content-Disposition";
    /** HTTP Response の Content-Disposition 正規表現 */
    private static final Pattern COTENT_DISPOSITION_PATTERN = Pattern.compile("attachment; filename\\*=([^']+)''(.+)");
    /** HTTP Response の Content-Disposition 正規表現中の文字コード部 */
    private static final int COTENT_DISPOSITION_PATTERN_CHARSET = 1;
    /** HTTP Response の Content-Disposition 正規表現中のファイル名部 */
    private static final int COTENT_DISPOSITION_PATTERN_FILENAME = 2;

    /**
     * 指定されたHttpResponseのHeader部から、ファイル名を取得する。
     * @param response HTTPレスポンスオブジェクト
     * @return ファイル名、取得できなかった場合null
     */
    private String getFileNameFromHeader(HttpResponse response) {
        Header header = response.getFirstHeader(CONTENT_DISPOSITION_HEADER_NAME);
        if (header == null) {
            logger.warn(LOG_MARKER, "Content-Disposition not found.");
            return null;
        }
        Matcher m = COTENT_DISPOSITION_PATTERN.matcher(header.getValue());
        if (!m.find()) {
            logger.warn(LOG_MARKER, "Illegal format Content-Disposition: {}", header.getValue());
            return null;
        }
        String rawCharset = m.group(COTENT_DISPOSITION_PATTERN_CHARSET);
        String rawFileName = m.group(COTENT_DISPOSITION_PATTERN_FILENAME);
        try {
            Charset charset = Charset.forName(rawCharset);
            return URLDecoder.decode(rawFileName, charset.name());
        } catch (UnsupportedEncodingException | IllegalCharsetNameException | UnsupportedCharsetException e) {
            logger.warn(LOG_MARKER, "Unsupported charset: {}", rawCharset);
            return null;
        }
    }

    /**
     * データセットのファイル一覧を取得する。（GET /api/datasets/${dataset_id}/files相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのファイル一覧
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<DatasetFile> getDatasetFiles(String datasetId, GetRangeParam param) {
        requireNotNull(datasetId, "at datasetId in getDatasetFiles");
        requireNotNull(param, "at param in getDatasetFiles");
        return get("/api/datasets/" + datasetId + "/files", param.toJsonString(), JsonUtil::toDatasetFiles);
    }

    /**
     * データセットのZIPファイルに含まれるファイル一覧を取得する。（GET /api/datasets/${dataset_id}/files/${fileId}/zippedfiles相当）
     * @param datasetId DatasetID
     * @param fileId FileID
     * @param param 一覧取得情報
     * @return ZIPファイル中のファイル一覧
     * @throws NullPointerException datasetId、fileId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<DatasetZipedFile> getDatasetZippedFiles(String datasetId, String fileId, GetRangeParam param) {
        requireNotNull(datasetId, "at datasetId in getDatasetZippedFiles");
        requireNotNull(fileId, "at fileId in getDatasetZippedFiles");
        requireNotNull(param, "at param in getDatasetZippedFiles");
        return get("/api/datasets/" + datasetId + "/files/" + fileId + "/zippedfiles", param.toJsonString(), JsonUtil::toDatasetZippedFiles);
    }

    /**
     * グループ一覧を取得する。（GET /api/groups相当）
     * @param param グループ一覧取得情報
     * @return グループ一覧情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<GroupsSummary> getGroups(GetGroupsParam param) {
        requireNotNull(param, "at param in getGroups");
        return get("/api/groups", param.toJsonString(), JsonUtil::toGroups);
    }

    /**
     * グループ詳細を取得する。（GET /api/groups/${group_id}相当）
     * @param groupId グループID
     * @return グループ詳細情報
     * @throws NullPointerException groupIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Group getGroup(String groupId) {
        requireNotNull(groupId, "at groupId in getGroup");
        return get("/api/groups/" + groupId, JsonUtil::toGroup);
    }

    /**
     * グループのメンバー一覧を取得する。（GET /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param グループメンバー一覧取得情報
     * @return グループメンバー一覧情報
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<MemberSummary> getMembers(String groupId, GetMembersParam param) {
        requireNotNull(groupId, "at groupId in getMembers");
        requireNotNull(param, "at param in getMembers");
        return get("/api/groups/" + groupId + "/members", param.toJsonString(), JsonUtil::toMembers);
    }

    /**
     * グループを作成する。（POST /api/groups相当）
     * @param param グループ作成情報
     * @return 作成したグループ詳細情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Group createGroup(CreateGroupParam param) {
        requireNotNull(param, "at param in getMembers");
        return post("/api/groups", param.toJsonString(), JsonUtil::toGroup);
    }

    /**
     * グループ詳細情報を更新する。（PUT /api/groups/${group_id}相当）
     * @param groupId グループID
     * @param param グループ詳細更新情報
     * @return グループ詳細情報
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Group updateGroup(String groupId, UpdateGroupParam param) {
        requireNotNull(groupId, "at groupId in updateGroup");
        requireNotNull(param, "at param in updateGroup");
        return put("/api/groups/" + groupId, param.toJsonString(), JsonUtil::toGroup);
    }

    /**
     * グループの画像一覧を取得する。（GET /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param param 一覧取得情報
     * @return グループの画像一覧情報
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<GroupGetImage> getGroupImage(String groupId, GetRangeParam param) {
        requireNotNull(groupId, "at groupId in getGroupImage");
        requireNotNull(param, "at param in getGroupImage");
        return get("/api/groups/" + groupId + "/images", param.toJsonString(), JsonUtil::toGroupGetImage);
    }

    /**
     * グループに画像を追加する。（POST /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param files 画像ファイル
     * @return 追加した画像ファイル情報
     * @throws NullPointerException groupId、files、filesの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public GroupAddImages addImagesToGroup(String groupId, File... files) {
        requireNotNull(groupId, "at groupId in addImagesToGroup");
        requireNotNull(files, "at files in addImagesToGroup");
        requireNotNullAll(files, "at files[%d] in addImagesToGroup");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            return builder.build();
        };
        return post("/api/groups/" + groupId + "/images", entity, JsonUtil::toGroupAddImages);
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。（PUT /api/groups/${group_id}/images/primary相当）
     * @param groupId グループID
     * @param param メイン画像指定情報
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        requireNotNull(groupId, "at groupId in setPrimaryImageToGroup");
        requireNotNull(param, "at param in setPrimaryImageToGroup");
        put("/api/groups/" + groupId + "/images/primary", param.toJsonString(), x -> x);
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     * @param groupId グループID
     * @param file 画像ファイル
     * @throws NullPointerException groupId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToGroup(String groupId, File file) {
        requireNotNull(groupId, "at groupId in setPrimaryImageToGroup");
        requireNotNull(file, "at file in setPrimaryImageToGroup");
        GroupAddImages image = addImagesToGroup(groupId, file);
        setPrimaryImageToGroup(groupId, new SetPrimaryImageParam(image.getImages().get(0).getId()));
    }

    /**
     * グループから画像を削除する。（DELETE /api/groups/${group_id}/images/${image_id}相当）
     * @param groupId グループID
     * @param imageId 画像ID
     * @return 画像削除後のグループのメイン画像情報
     * @throws NullPointerException groupId、imageIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public GroupDeleteImage deleteImageToGroup(String groupId, String imageId) {
        requireNotNull(groupId, "at groupId in deleteImageToGroup");
        requireNotNull(imageId, "at imageId in deleteImageToGroup");
        return delete("/api/groups/" + groupId + "/images/" + imageId, JsonUtil::toGroupDeleteImage);
    }

    /**
     * グループにメンバーを追加する。（POST /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param メンバー追加情報
     * @throws NullPointerException groupId、params、paramsの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void addMember(String groupId, List<AddMemberParam> params) {
        requireNotNull(groupId, "at groupId in addMember");
        requireNotNull(params, "at params in addMember");
        requireNotNullAll(params, "at params[%s] in addMember");
        post("/api/groups/" + groupId + "/members", AddMemberParam.toJsonString(params), x -> x);
    }

    /**
     * メンバーのロールを設定する。（PUT /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     * @param param ロール設定情報
     * @throws NullPointerException groupId、userId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setMemberRole(String groupId, String userId, SetMemberRoleParam param) {
        requireNotNull(groupId, "at groupId in setMemberRole");
        requireNotNull(userId, "at userId in setMemberRole");
        requireNotNull(param, "at param in setMemberRole");
        put("/api/groups/" + groupId + "/members/" + userId, param.toJsonString(), x -> x);
    }

    /**
     * メンバーを削除する。（DELETE /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     * @throws NullPointerException groupId、userIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteMember(String groupId, String userId) {
        requireNotNull(groupId, "at groupId in deleteMember");
        requireNotNull(userId, "at userId in deleteMember");
        delete("/api/groups/" + groupId + "/members/" + userId, x -> x);
    }

    /**
     * グループを削除する。（DELETE /api/groups/${group_id}相当）
     * @param groupId グループID
     * @throws NullPointerException groupIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteGroup(String groupId) {
        requireNotNull(groupId, "at groupId in deleteGroup");
        delete("/api/groups/" + groupId, x -> x);
    }

    /**
     * ログインユーザのプロファイルを取得する。（GET /api/profile相当）
     * @return プロファイル
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User getProfile() {
        return get("/api/profile", JsonUtil::toUser);
    }

    /**
     * ログインユーザのプロファイルを更新する。（PUT /api/profile相当）
     * @param param プロファイル更新情報
     * @return プロファイル
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User updateProfile(UpdateProfileParam param) {
        requireNotNull(param, "at param in updateProfile");
        return put("/api/profile", param.toJsonString(), JsonUtil::toUser);
    }

    /**
     * ログインユーザの画像を更新する。（POST /api/profile/image相当）
     * @param file 画像ファイル
     * @return プロファイル
     * @throws NullPointerException fileがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User updateProfileIcon(File file) {
        requireNotNull(file, "at file in updateProfileIcon");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("icon", file);
            return builder.build();
        };
        return post("/api/profile/image", entity, JsonUtil::toUser);
    }

    /**
     * ログインユーザのE-Mailを変更する。（POST /api/profile/email_change_request相当）
     * @param param E-Mail変更情報
     * @return プロファイル
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User updateEmail(UpdateEmailParam param) {
        requireNotNull(param, "at param in updateEmail");
        return post("/api/profile/email_change_requests", param.toJsonString(), JsonUtil::toUser);
    }

    /**
     * ログインユーザのパスワードを変更する。（PUT /api/profile/password相当）
     * @param param パスワード変更情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void changePassword(ChangePasswordParam param) {
        requireNotNull(param, "at param in changePassword");
        put("/api/profile/password", param.toJsonString(), x -> x);
    }

    /**
     * ユーザー一覧を取得する。（GET /api/accounts相当）
     * @return ユーザー一覧
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public List<User> getAccounts() {
        return get("/api/accounts", JsonUtil::toUsers);
    }

    /**
     * ライセンス一覧を取得する。（GET /api/licenses相当）
     * @return ライセンス一覧情報
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public List<License> getLicenses() {
        return get("/api/licenses", JsonUtil::toLicenses);
    }

    /**
     * タスクの現在のステータスを取得する。（GET /api/tasks/${task_id}相当）
     * @param taskId タスクID
     * @return タスクのステータス情報
     * @throws NullPointerException taskIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public TaskStatus getTaskStatus(String taskId) {
        requireNotNull(taskId, "at taskId in getTaskStatus");
        return get("/api/tasks/" + taskId, JsonUtil::toTaskStatus);
    }

    /**
     * 統計情報を取得します。（GET /api/statistics相当）
     * @param param 統計情報期間指定
     * @return 統計情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public List<StatisticsDetail> getStatistics(StatisticsParam param) {
        requireNotNull(param, "at param in getStatistics");
        return get("/api/statistics", param.toJsonString(), JsonUtil::toStatistics);
    }

    /**
     * リクエストを送信する。
     * @param request リクエストのサプライヤ
     * @param ext リクエストに対する追加処理
     * @param f レスポンス変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T extends HttpUriRequest & AutoCloseable, R> R send(ExceptionSupplier<T> request, Consumer<T> ext, ResponseFunction<R> f) {
        try (T req = request.get()) {
            addAuthorizationHeader(req);
            if (ext != null) {
                ext.accept(req);
            }
            return execute(req, f);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
    }

    /**
     * HEADリクエストを送信する。
     * @param url 送信先URL
     * @param f レスポンス変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T head(String url, ResponseFunction<T> f) {
        return send(() -> new AutoHttpHead(_baseUrl + url), null, f);
    }

    /**
     * GETリクエストを送信する。
     * @param url 送信先URL
     * @param ext リクエストに対する追加処理
     * @param f レスポンス変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, Consumer<AutoHttpGet> ext, ResponseFunction<T> f) {
        return send(() -> new AutoHttpGet(_baseUrl + url), ext, f);
    }

    /**
     * GETリクエストを送信する。
     * @param url 送信先URL
     * @param f レスポンス変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, ResponseFunction<T> f) {
        return get(url, null, f);
    }

    /**
     * GETリクエストを送信する。
     * @param url 送信先URL
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, Function<String, T> f) {
        return get(url, (HttpResponse response) -> f.apply(responseToString(response)));
    }

    /**
     * GETリクエストを送信する。
     * @param url 送信先URL
     * @param jsonParam リクエストに付与するJSONパラメータ
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, String jsonParam, Function<String, T> f) {
        return send(
            () -> new AutoHttpGet(_baseUrl + url + "?d=" + URLEncoder.encode(jsonParam, StandardCharsets.UTF_8.name())),
            null,
            (HttpResponse response) -> f.apply(responseToString(response))
        );
    }

    /**
     * POSTリクエストを送信する。
     * @param url 送信先URL
     * @param ext リクエストに対する追加処理
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, Consumer<AutoHttpPost> ext, Function<String, T> f) {
        return send(
            () -> new AutoHttpPost(_baseUrl + url),
            ext,
            (HttpResponse response) -> f.apply(responseToString(response))
        );
    }

    /**
     * POSTリクエストを送信する。
     * @param url 送信先URL
     * @param entity リクエストボディのサプライヤ
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, Supplier<HttpEntity> entity, Function<String, T> f) {
        Consumer<AutoHttpPost> ext = entity == null ? null : req -> {
            req.setEntity(entity.get());
        };
        return post(url, ext, f);
    }

    /**
     * POSTリクエストを送信する。
     * @param url 送信先URL
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, Function<String, T> f) {
        return post(url, (Supplier<HttpEntity>) null, f);
    }

    /**
     * POSTリクエストを送信する。
     * @param url 送信先URL
     * @param jsonParam リクエストボディに付与するJSONパラメータ
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, String jsonParam, Function<String, T> f) {
        return post(url, () -> toHttpEntity(jsonParam), f);
    }

    /**
     * PUTリクエストを送信する。
     * @param url 送信先URL
     * @param ext リクエストに対する追加処理
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T put(String url, Consumer<AutoHttpPut> ext, Function<String, T> f) {
        return send(
            () -> new AutoHttpPut(_baseUrl + url),
            ext,
            (HttpResponse response) -> f.apply(responseToString(response))
        );
    }

    /**
     * PUTリクエストを送信する。
     * @param url 送信先URL
     * @param entity リクエストボディのサプライヤ
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T put(String url, Supplier<HttpEntity> entity, Function<String, T> f) {
        Consumer<AutoHttpPut> ext = entity == null ? null : req -> {
            req.setEntity(entity.get());
        };
        return put(url, ext, f);
    }

    /**
     * PUTリクエストを送信する。
     * @param url 送信先URL
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T put(String url, Function<String, T> f) {
        return put(url, (Supplier<HttpEntity>) null, f);
    }

    /**
     * PUTリクエストを送信する。
     * @param url 送信先URL
     * @param jsonParam リクエストボディに付与するJSONパラメータ
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T put(String url, String jsonParam, Function<String, T> f) {
        return put(url, () -> toHttpEntity(jsonParam), f);
    }

    /**
     * DELETEリクエストを送信する。
     * @param url 送信先URL
     * @param f レスポンスボディ変換関数
     * @return fの変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T delete(String url, Function<String, T> f) {
        return send(
            () -> new AutoHttpDelete(_baseUrl + url),
            null,
            (HttpResponse response) -> f.apply(responseToString(response))
        );
    }

    /**
     * JSONパラメータをリクエストボディ用に変換する。
     * @param jsonParam 変換するJSON文字列
     * @return 変換結果
     */
    private static HttpEntity toHttpEntity(String jsonParam) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("d", jsonParam));
        return new UrlEncodedFormEntity(params, StandardCharsets.UTF_8);
    }

    /**
     * Authorizationヘッダを追加する。
     * @param request リクエストオブジェクト
     */
    void addAuthorizationHeader(HttpUriRequest request) {
        if (! _apiKey.isEmpty() && ! _secretKey.isEmpty()) {
            request.addHeader("Authorization", String.format("api_key=%s, signature=%s",  _apiKey, getSignature(_apiKey, _secretKey)));
        }
    }

    /**
     * 認証文字列を作成する。
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     * @return 作成した認証文字列
     */
    private String getSignature(String apiKey, String secretKey) {
        try {
            SecretKeySpec sk = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(sk);
            byte[] result = mac.doFinal((apiKey + "&" + secretKey).getBytes());
            return URLEncoder.encode(Base64.getEncoder().encodeToString(result), "UTF-8");
        } catch (Exception e) {
            logger.error(LOG_MARKER, resource.getString(ResourceNames.ERROR_OCCURED), e.getMessage());
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /** デフォルトのレスポンスボディ文字コード */
    public static final Charset DEFAULT_RESPONSE_CHAESET = StandardCharsets.UTF_8;
    
    /**
     * レスポンスからレスポンスボディの文字列表現を取得する。
     * レスポンスヘッダに文字コード指定がない場合、デフォルトの文字コード(UTF-8)を使用する
     * @param response レスポンス
     * @return レスポンスボディの文字列表現
     */
    private String responseToString(HttpResponse response) throws IOException {
        return responseToString(response, DEFAULT_RESPONSE_CHAESET.name());
    }
    
    /**
     * レスポンスからレスポンスボディの文字列表現を取得する。
     * @param response レスポンス
     * @param charset レスポンスヘッダに文字コード指定がない場合に使用する文字コード
     * @return レスポンスボディの文字列表現
     */
    private String responseToString(HttpResponse response, String charset) throws IOException {
        return EntityUtils.toString(response.getEntity(), charset);
    }

    /**
     * リクエストを実行する。
     * @param <T> レスポンス変換後の型
     * @param request リクエスト
     * @param f レスポンス変換関数
     * @return fの変換結果
     * @throws IOException 接続に失敗した場合
     * @throws HttpException レスポンスがHTTPレスポンスとして不正な場合 
     * @throws ErrorRespondedException エラーレスポンスが返ってきた場合
     */
    private <T> T execute(HttpUriRequest request, ResponseFunction<T> f) throws IOException, HttpException, ErrorRespondedException {
        try (AutoCloseHttpClient client = createHttpClient()) {
            HttpResponse response = client.execute(request);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 400) {
                throw new ErrorRespondedException(response);
            }
            return f.apply(response);
        }
    }

    /**
     * HTTPクライアントを取得する。
     * @return HTTPクライアント
     */
    private AutoCloseHttpClient createHttpClient() {
        return new AutoCloseHttpClient();
    }

    /**
     * 内部で送出される例外を、公開用に翻訳する。
     * @param e 内部で送出される例外
     * @return 公開用に翻訳された例外
     */
    private RuntimeException translateInnerException(Exception e) {
        logger.error(LOG_MARKER, resource.getString(ResourceNames.ERROR_OCCURED), e.getMessage());
        if (e instanceof ErrorRespondedException) {
            return new HttpStatusException(((ErrorRespondedException) e).getStatusCode(), e);
        }
        if (e instanceof SocketTimeoutException) {
            return new TimeoutException(e.getMessage(), e);
        }
        if (e instanceof HttpHostConnectException) {
            return new ConnectionLostException(e.getMessage(), e);
        }
        return new ApiFailedException(e.getMessage(), e);
    }
}
