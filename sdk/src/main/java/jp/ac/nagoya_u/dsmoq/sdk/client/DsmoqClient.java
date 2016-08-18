package jp.ac.nagoya_u.dsmoq.sdk.client;

import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireGreaterOrEqualOrNull;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotEmpty;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotNull;
import static jp.ac.nagoya_u.dsmoq.sdk.util.CheckUtil.requireNotNullAll;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

/**
 * dsmoq APIを叩くためのクライアントクラス
 * 
 * 個々のWeb APIの仕様については、APIのドキュメントを参照してください。
 */
public class DsmoqClient {
    /** HTTP Request の Authorization ヘッダ */
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    /** HTTP Response の Content-Disposition ヘッダ */
    private static final String CONTENT_DISPOSITION_HEADER_NAME = "Content-Disposition";

    /** HTTP Response の Content-Disposition ヘッダ */
    private static final String CONTENT_LENGTH_HEADER_NAME = "Content-Length";

    /** HTTP Response の Content-Disposition 正規表現 */
    private static final Pattern COTENT_DISPOSITION_PATTERN = Pattern.compile("attachment; filename\\*=([^']+)''(.+)");

    /** HTTP Response の Content-Disposition 正規表現中の文字コード部 */
    private static final int COTENT_DISPOSITION_PATTERN_CHARSET = 1;

    /** HTTP Response の Content-Disposition 正規表現中のファイル名部 */
    private static final int COTENT_DISPOSITION_PATTERN_FILENAME = 2;

    /** デフォルトのリクエストボディ文字コード */
    private static final Charset DEFAULT_REQUEST_CHARSET = StandardCharsets.UTF_8;

    /** デフォルトのレスポンスボディ文字コード */
    private static final Charset DEFAULT_RESPONSE_CHARSET = StandardCharsets.UTF_8;

    /** exportAttributeの際に用いるファイル名 */
    private static final String EXPORT_ATTRIBUTE_CSV_FILENAME = "export.csv";

    /** 認証文字列の生成に利用するハッシュアルゴリズム */
    private static final String HASH_ALGORITHM = "HmacSHA1";

    /** ログマーカー */
    private static final Marker LOG_MARKER = MarkerFactory.getMarker("SDK");

    /** ロガー */
    private static Logger logger = LoggerFactory.getLogger(LOG_MARKER.toString());

    /** HTTP Response の Content-Disposition ヘッダ */
    private static final String RANGE_HEADER_NAME = "Range";

    /** JSONパラメータを乗せるリクエストボディのパラメータ名 */
    private static final String REQUEST_JSON_PARAM_NAME = "d";

    /** メッセージ用のリソースバンドル */
    private static ResourceBundle resource = ResourceBundle.getBundle("message");

    /**
     * APIキー、シークレットキーを使用するクライアントオブジェクトを生成する。
     * 
     * @param baseUrl 基準となるURL
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     * @return 作成したクライアント
     */
    public static DsmoqClient create(String baseUrl, String apiKey, String secretKey) {
        return new DsmoqClient(baseUrl, apiKey, secretKey);
    }

    /**
     * 指定されたHTTPステータスコードが、エラーレスポンスを表すかを返す。
     * 
     * @param statusCode HTTPステータスコード
     * @return statusCodeがエラーレスポンスを表す場合true、そうでなければfalse
     */
    private static boolean isErrorStatus(int statusCode) {
        return statusCode >= 400;
    }

    /**
     * JSONパラメータをリクエストボディ用に変換する。
     * 
     * @param jsonParam 変換するJSON文字列
     * @return 変換結果
     */
    private static HttpEntity toHttpEntity(String jsonParam) {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair(REQUEST_JSON_PARAM_NAME, jsonParam));
        return new UrlEncodedFormEntity(params, DEFAULT_REQUEST_CHARSET);
    }

    /** APIキー */
    private String _apiKey;

    /** 基準となるURL */
    private String _baseUrl;

    /** シークレットキー */
    private String _secretKey;

    /**
     * クライアントオブジェクトを生成する。
     * 
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
     * Datasetにファイルを追加する。
     * 
     * POST /api/datasets/${dataset_id}/files を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#addFiles start : [datasetId] = {}, [file num] = {}", datasetId,
                (files == null) ? "null" : files.length);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#addFiles");
        requireNotNull(files, "at files in DsmoqClient#addFiles");
        requireNotNullAll(files, "at files[%d] in DsmoqClient#addFiles");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // MultipartEntityBuilderのモード互換モードを設定
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            // MultipartEntityBuilderの文字コードにutf-8を設定
            builder.setCharset(StandardCharsets.UTF_8);
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("files", file));
            return builder.build();
        };
        return post("/api/datasets/" + datasetId + "/files", entity, JsonUtil::toDatasetAddFiles);
    }

    /**
     * データセットに画像を追加する。
     * 
     * POST /api/datasets/${dataset_id}/image を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#addImagesToDataset start : [datasetId] = {}, [file num] = {}", datasetId,
                (files == null) ? "null" : files.length);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#addImagesToDataset");
        requireNotNull(files, "at files in DsmoqClient#addImagesToDataset");
        requireNotNullAll(files, "at files[%d] in DsmoqClient#addImagesToDataset");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            return builder.build();
        };
        return post("/api/datasets/" + datasetId + "/images", entity, JsonUtil::toDatasetAddImages);
    }

    /**
     * グループに画像を追加する。
     *
     * POST /api/groups/${group_id}/images を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#addImagesToGroup start : [groupId] = {}, [file num] = {}", groupId,
                (files == null) ? "null" : files.length);
        requireNotNull(groupId, "at groupId in DsmoqClient#addImagesToGroup");
        requireNotNull(files, "at files in DsmoqClient#addImagesToGroup");
        requireNotNullAll(files, "at files[%d] in DsmoqClient#addImagesToGroup");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            return builder.build();
        };
        return post("/api/groups/" + groupId + "/images", entity, JsonUtil::toGroupAddImages);
    }

    /**
     * グループにメンバーを追加する。
     *
     * POST /api/groups/${group_id}/members を呼ぶ。
     * 
     * @param groupId グループID
     * @param param メンバー追加情報
     * @throws NullPointerException groupId、params、paramsの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void addMember(String groupId, List<AddMemberParam> params) {
        logger.debug(LOG_MARKER, "DsmoqClient#addMember start : [groupId] = {}, [params] = {}", groupId, params);
        requireNotNull(groupId, "at groupId in DsmoqClient#addMember");
        requireNotNull(params, "at params in DsmoqClient#addMember");
        requireNotNullAll(params, "at params[%s] in DsmoqClient#addMember");
        post("/api/groups/" + groupId + "/members", AddMemberParam.toJsonString(params), x -> x);
    }

    /**
     * データセットのアクセス権を変更する。
     *
     * POST /api/datasets/${dataset_id}/acl を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#changeAccessLevel start : [datasetId] = {}, [params] = {}", datasetId,
                params);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#changeAccessLevel");
        requireNotNull(params, "at params in DsmoqClient#changeAccessLevel");
        requireNotNullAll(params, "at params[%d] in DsmoqClient#changeAccessLevel");
        return post("/api/datasets/" + datasetId + "/acl", SetAccessLevelParam.toJsonString(params),
                JsonUtil::toDatasetOwnerships);
    }

    /**
     * データセットの保存先を変更する。
     *
     * PUT /api/datasets/${dataset_id}/storage を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#changeDatasetStorage start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#changeDatasetStorage");
        requireNotNull(param, "at param in DsmoqClient#changeDatasetStorage");
        return put("/api/datasets/" + datasetId + "/storage", param.toJsonString(), JsonUtil::toDatasetTask);
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。
     *
     * PUT /api/datasets/${dataset_id}/guest_access を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#changeGuestAccessLevel start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#changeGuestAccessLevel");
        requireNotNull(param, "at param in DsmoqClient#changeGuestAccessLevel");
        put("/api/datasets/" + datasetId + "/guest_access", param.toJsonString(), x -> x);
    }

    /**
     * ログインユーザのパスワードを変更する。
     *
     * PUT /api/profile/password を呼ぶ。
     * 
     * @param param パスワード変更情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void changePassword(ChangePasswordParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#changePassword start : [param] = {}", param);
        requireNotNull(param, "at param in DsmoqClient#changePassword");
        put("/api/profile/password", param.toJsonString(), x -> x);
    }

    /**
     * データセットをコピーする。
     *
     * POST /api/datasets/${dataset_id}/copy を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @return コピーしたDatasetID
     * @throws NullPointerException datasetIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public String copyDataset(String datasetId) {
        logger.debug(LOG_MARKER, "DsmoqClient#copyDataset start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#copyDataset");
        return post("/api/datasets/" + datasetId + "/copy", json -> JsonUtil.toCopiedDataset(json).getDatasetId());
    }

    /**
     * Datasetを作成する。
     *
     * POST /api/datasets を呼ぶ。
     * 作成されるDatasetの名前は、最初に指定されたファイル名となる。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#createDataset start : [saveLocal] = {}, [saveS3] = {}, [file num] = {}",
                saveLocal, saveS3, (files == null) ? "null" : files.length);
        requireNotNull(files, "at files in DsmoqClient#createDataset");
        requireNotEmpty(files, "at files in DsmoqClient#createDataset");
        requireNotNullAll(files, "at files[%d] in DsmoqClient#createDataset");
        return createDataset(files[0].getName(), saveLocal, saveS3, files);
    }

    /**
     * Datasetを作成する。
     *
     * POST /api/datasets を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#createDataset start : [name] = {}, [saveLocal] = {}, [saveS3] = {}", name,
                saveLocal, saveS3);
        requireNotNull(name, "at name in DsmoqClient#createDataset");
        return createDataset(name, saveLocal, saveS3, new File[0]);
    }

    /**
     * Datasetを作成する。
     *
     * POST /api/datasets を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER,
                "DsmoqClient#createDataset start : [name] = {}, [saveLocal] = {}, [saveS3] = {}, [files size] = {}",
                name, saveLocal, saveS3, (files == null) ? "null" : files.length);
        requireNotNull(name, "at name in DsmoqClient#createDataset");
        requireNotNull(files, "at files in DsmoqClient#createDataset");
        requireNotNullAll(files, "at files[%d] in DsmoqClient#createDataset");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // MultipartEntityBuilderのモード互換モードを設定
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            // MultipartEntityBuilderの文字コードにutf-8を設定
            builder.setCharset(StandardCharsets.UTF_8);
            // 送信データに"name"(データセットの名前)を追加(文字コード明示)
            builder.addTextBody("name", name, ContentType.create("text/plain", DEFAULT_REQUEST_CHARSET));
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("file[]", file));
            builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
            builder.addTextBody("saveS3", saveS3 ? "true" : "false");
            return builder.build();
        };
        return post("/api/datasets", entity, JsonUtil::toDataset);
    }

    /**
     * グループを作成する。
     *
     * POST /api/groups を呼ぶ。
     * 
     * @param param グループ作成情報
     * @return 作成したグループ詳細情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Group createGroup(CreateGroupParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#createGroup start : [param] = {}", param);
        requireNotNull(param, "at param in DsmoqClient#createGroup");
        return post("/api/groups", param.toJsonString(), JsonUtil::toGroup);
    }

    /**
     * データセットを削除する。
     *
     * DELETE /api/datasets/${dataset_id} を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @throws NullPointerException datasetIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteDataset(String datasetId) {
        logger.debug(LOG_MARKER, "DsmoqClient#deleteDataset start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#deleteDataset");
        delete("/api/datasets/" + datasetId, x -> x);
    }

    /**
     * データセットからファイルを削除する。
     *
     * DELETE /api/datasets/${dataset_id}/files/${file_id} を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @throws NullPointerException datasetId、fileIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteFile(String datasetId, String fileId) {
        logger.debug(LOG_MARKER, "DsmoqClient#deleteFile start : [datasetId] = {}, [fileId] = {}", datasetId, fileId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#deleteFile");
        requireNotNull(fileId, "at fileId in DsmoqClient#deleteFile");
        delete("/api/datasets/" + datasetId + "/files/" + fileId, x -> x);
    }

    /**
     * グループを削除する。
     *
     * DELETE /api/groups/${group_id} を呼ぶ。
     * 
     * @param groupId グループID
     * @throws NullPointerException groupIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteGroup(String groupId) {
        logger.debug(LOG_MARKER, "DsmoqClient#deleteGroup start : [groupId] = {}", groupId);
        requireNotNull(groupId, "at groupId in DsmoqClient#deleteGroup");
        delete("/api/groups/" + groupId, x -> x);
    }

    /**
     * データセットから画像を削除する。
     *
     * DELETE /api/datasets/${dataset_id}/image/${image_id} を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#deleteImageToDataset start : [datasetId] = {}, [imageId] = {}", datasetId,
                imageId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#deleteImageToDataset");
        requireNotNull(imageId, "at imageId in DsmoqClient#deleteImageToDataset");
        return delete("/api/datasets/" + datasetId + "/images/" + imageId, JsonUtil::toDatasetDeleteImage);
    }

    /**
     * グループから画像を削除する。
     *
     * DELETE /api/groups/${group_id}/images/${image_id} を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#deleteImageToGroup start : [groupId] = {}, [imageId] = {}", groupId,
                imageId);
        requireNotNull(groupId, "at groupId in DsmoqClient#deleteImageToGroup");
        requireNotNull(imageId, "at imageId in DsmoqClient#deleteImageToGroup");
        return delete("/api/groups/" + groupId + "/images/" + imageId, JsonUtil::toGroupDeleteImage);
    }

    /**
     * メンバーを削除する。
     *
     * DELETE /api/groups/${group_id}/members/${user_id} を呼ぶ。
     * 
     * @param groupId グループID
     * @param userId ユーザーID
     * @throws NullPointerException groupId、userIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void deleteMember(String groupId, String userId) {
        logger.debug(LOG_MARKER, "DsmoqClient#deleteMember start : [groupId] = {}, [userId] = {}", groupId, userId);
        requireNotNull(groupId, "at groupId in DsmoqClient#deleteMember");
        requireNotNull(userId, "at userId in DsmoqClient#deleteMember");
        delete("/api/groups/" + groupId + "/members/" + userId, x -> x);
    }

    /**
     * データセットからファイルをダウンロードする。
     *
     * GET /files/${dataset_id}/${file_id} を呼ぶ。
     * 
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param datasetFileFunc ファイルデータを処理する関数 (引数のDatasetFileはこの処理関数中でのみ利用可能)
     * @return 処理結果
     * @throws NullPointerException datasetIdまたはfileIdまたはdatasetFileFuncがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public <T> T downloadFile(String datasetId, String fileId, Function<DatasetFileContent, T> datasetFileFunc) {
        logger.debug(LOG_MARKER, "DsmoqClient#downloadFile start : [datasetId] = {}, [fileId] = {}", datasetId, fileId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#downloadFile");
        requireNotNull(fileId, "at fileId in DsmoqClient#downloadFile");
        requireNotNull(datasetFileFunc, "at datasetFileFunc in DsmoqClient#downloadFile");
        return downloadFileWithRange(datasetId, fileId, null, null, datasetFileFunc);
    }

    /**
     * データセットからファイルの内容を部分的に取得する。
     *
     * GET /files/${dataset_id}/${file_id} を呼ぶ。
     * 
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param from 開始位置指定、指定しない場合null
     * @param to 終了位置指定、指定しない場合null
     * @param datasetFileFunc ファイルデータを処理する関数
     *            (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return 処理結果
     * @throws NullPointerException datasetIdまたはfileIdまたはdatasetFileFuncがnullの場合
     * @throws IllegalArgumentException fromまたはtoが0未満の場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public <T> T downloadFileWithRange(String datasetId, String fileId, Long from, Long to,
            Function<DatasetFileContent, T> datasetFileFunc) {
        logger.debug(LOG_MARKER,
                "DsmoqClient#downloadFileWithRange start : [datasetId] = {}, [fileId] = {}, [from:to] = {}:{}",
                datasetId, fileId, from, to);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#downloadFileWithRange");
        requireNotNull(fileId, "at fileId in DsmoqClient#downloadFileWithRange");
        requireNotNull(datasetFileFunc, "at datasetFileFunc in DsmoqClient#downloadFileWithRange");
        requireGreaterOrEqualOrNull(from, 0L, "at from in DsmoqClient#downloadFileWithRange");
        requireGreaterOrEqualOrNull(to, 0L, "at to in DsmoqClient#downloadFileWithRange");
        return get("/files/" + datasetId + "/" + fileId, request -> {
            if (from != null || to != null) {
                request.setHeader(RANGE_HEADER_NAME, String.format("bytes=%s-%s", from == null ? "" : from.toString(),
                        to == null ? "" : to.toString()));
            }
        } , response -> {
            String filename = getFileNameFromHeader(response);
            return datasetFileFunc.apply(new DatasetFileContent() {
                public InputStream getContent() throws IOException {
                    return response.getEntity().getContent();
                }

                public String getName() {
                    return filename;
                }

                public void writeTo(OutputStream s) throws IOException {
                    response.getEntity().writeTo(s);
                }
            });
        });
    }

    /**
     * CSV形式のAttributeを取得する。
     *
     * GET /api/datasets/${dataset_id}/attributes/export を呼ぶ。
     * 
     * @param <T> CSVデータ処理後の型
     * @param datasetId DatasetID
     * @param fileFunc CSVデータを処理する関数 (引数のDatasetFileContentはこの処理関数中でのみ利用可能)
     * @return 処理結果
     * @throws NullPointerException datasetIdまたはfileFuncがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public <T> T exportAttribute(String datasetId, Function<DatasetFileContent, T> fileFunc) {
        logger.debug(LOG_MARKER, "DsmoqClient#exportAttribute start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#exportAttribute");
        requireNotNull(fileFunc, "at fileFunc in DsmoqClient#exportAttribute");
        return get("/api/datasets/" + datasetId + "/attributes/export", (HttpResponse response) -> {
            return fileFunc.apply(new DatasetFileContent() {
                public InputStream getContent() throws IOException {
                    return response.getEntity().getContent();
                }

                public String getName() {
                    return EXPORT_ATTRIBUTE_CSV_FILENAME;
                }

                public void writeTo(OutputStream s) throws IOException {
                    response.getEntity().writeTo(s);
                }
            });
        });
    }

    /**
     * データセットのアクセス権一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/acl を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#getAccessLevel start : [datasetId] = {}, [param] = {}", datasetId, param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#getAccessLevel");
        requireNotNull(param, "at param in DsmoqClient#getAccessLevel");
        return get("/api/datasets/" + datasetId + "/acl", param.toJsonString(), JsonUtil::toDatasetOwnership);
    }

    /**
     * ユーザー一覧を取得する。
     *
     * GET /api/accounts を呼ぶ。
     * 
     * @return ユーザー一覧
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public List<User> getAccounts() {
        logger.debug(LOG_MARKER, "DsmoqClient#getAccounts start");
        return get("/api/accounts", JsonUtil::toUsers);
    }

    /**
     * Datasetを取得する。
     *
     * GET /api/datasets/${dataset_id} を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @return 取得結果
     * @throws NullPointerException datasetIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Dataset getDataset(String datasetId) {
        logger.debug(LOG_MARKER, "DsmoqClient#getDataset start : [datasetId] = {}", datasetId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#getDataset");
        return get("/api/datasets/" + datasetId, JsonUtil::toDataset);
    }

    /**
     * データセットのファイル一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/files を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#getDatasetFiles start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#getDatasetFiles");
        requireNotNull(param, "at param in DsmoqClient#getDatasetFiles");
        return get("/api/datasets/" + datasetId + "/files", param.toJsonString(), JsonUtil::toDatasetFiles);
    }

    /**
     * データセットの画像一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/image を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#getDatasetImage start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#getDatasetImage");
        requireNotNull(param, "at param in DsmoqClient#getDatasetImage");
        return get("/api/datasets/" + datasetId + "/images", param.toJsonString(), JsonUtil::toDatasetGetImage);
    }

    /**
     * Datasetを検索する。
     *
     * GET /api/datasets を呼ぶ。
     * 
     * @param param Dataset検索に使用するパラメタ
     * @return 検索結果
     * @throws NullPointerException paramsがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<DatasetsSummary> getDatasets(GetDatasetsParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#getDatasets start : [param] = {}", param);
        requireNotNull(param, "at param in DsmoqClient#getDatasets");
        return get("/api/datasets", param.toJsonString(), JsonUtil::toDatasets);
    }

    /**
     * データセットのZIPファイルに含まれるファイル一覧を取得する。
     *
     * GET /api/datasets/${dataset_id}/files/${fileId}/zippedfiles を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER,
                "DsmoqClient#getDatasetZippedFiles start : [datasetId] = {}, [fileId] = {}, [param] = {}", datasetId,
                fileId, param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#getDatasetZippedFiles");
        requireNotNull(fileId, "at fileId in DsmoqClient#getDatasetZippedFiles");
        requireNotNull(param, "at param in DsmoqClient#getDatasetZippedFiles");
        return get("/api/datasets/" + datasetId + "/files/" + fileId + "/zippedfiles", param.toJsonString(),
                JsonUtil::toDatasetZippedFiles);
    }

    /**
     * データセットに設定されているファイルのサイズを取得する。
     *
     * HEAD /files/${dataset_id}/${file_id} を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#getFileSize start : [datasetId] = {}, [fileId] = {}", datasetId, fileId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#getFileSize");
        requireNotNull(fileId, "at fileId in DsmoqClient#getFileSize");
        return head("/files/" + datasetId + "/" + fileId, response -> {
            Header header = response.getFirstHeader(CONTENT_LENGTH_HEADER_NAME);
            if (header == null) {
                logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_CONTENT_LENGTH_NOT_FOUND));
                return null;
            }
            try {
                return Long.valueOf(header.getValue());
            } catch (NumberFormatException e) {
                logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_INVALID_CONTENT_LENGTH), header.getValue(),
                        e);
                return null;
            }
        });
    }

    /**
     * グループ詳細を取得する。
     *
     * GET /api/groups/${group_id} を呼ぶ。
     * 
     * @param groupId グループID
     * @return グループ詳細情報
     * @throws NullPointerException groupIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Group getGroup(String groupId) {
        logger.debug(LOG_MARKER, "DsmoqClient#getGroup start : [groupId] = {}", groupId);
        requireNotNull(groupId, "at groupId in DsmoqClient#getGroup");
        return get("/api/groups/" + groupId, JsonUtil::toGroup);
    }

    /**
     * グループの画像一覧を取得する。
     *
     * GET /api/groups/${group_id}/images を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#getGroupImage start : [groupId] = {}, [param] = {}", groupId, param);
        requireNotNull(groupId, "at groupId in DsmoqClient#getGroupImage");
        requireNotNull(param, "at param in DsmoqClient#getGroupImage");
        return get("/api/groups/" + groupId + "/images", param.toJsonString(), JsonUtil::toGroupGetImage);
    }

    /**
     * グループ一覧を取得する。
     *
     * GET /api/groups を呼ぶ。
     * 
     * @param param グループ一覧取得情報
     * @return グループ一覧情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public RangeSlice<GroupsSummary> getGroups(GetGroupsParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#getGroups start : [param] = {}", param);
        requireNotNull(param, "at param in DsmoqClient#getGroups");
        return get("/api/groups", param.toJsonString(), JsonUtil::toGroups);
    }

    /**
     * ライセンス一覧を取得する。
     *
     * GET /api/licenses を呼ぶ。
     * 
     * @return ライセンス一覧情報
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public List<License> getLicenses() {
        logger.debug(LOG_MARKER, "DsmoqClient#getLicenses start");
        return get("/api/licenses", JsonUtil::toLicenses);
    }

    /**
     * グループのメンバー一覧を取得する。
     *
     * GET /api/groups/${group_id}/members を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#getMembers start : [groupId] = {}, [param] = {}", groupId, param);
        requireNotNull(groupId, "at groupId in DsmoqClient#getMembers");
        requireNotNull(param, "at param in DsmoqClient#getMembers");
        return get("/api/groups/" + groupId + "/members", param.toJsonString(), JsonUtil::toMembers);
    }

    /**
     * ログインユーザのプロファイルを取得する。
     *
     * GET /api/profile を呼ぶ。
     * 
     * @return プロファイル
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User getProfile() {
        logger.debug(LOG_MARKER, "DsmoqClient#getProfile start");
        return get("/api/profile", JsonUtil::toUser);
    }

    /**
     * 統計情報を取得します。
     *
     * GET /api/statistics を呼ぶ。
     * 
     * @param param 統計情報期間指定
     * @return 統計情報
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public List<StatisticsDetail> getStatistics(StatisticsParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#getStatistics start : [param] = {}", param);
        requireNotNull(param, "at param in DsmoqClient#getStatistics");
        return get("/api/statistics", param.toJsonString(), JsonUtil::toStatistics);
    }

    /**
     * タスクの現在のステータスを取得する。
     *
     * GET /api/tasks/${task_id} を呼ぶ。
     * 
     * @param taskId タスクID
     * @return タスクのステータス情報
     * @throws NullPointerException taskIdがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public TaskStatus getTaskStatus(String taskId) {
        logger.debug(LOG_MARKER, "DsmoqClient#getTaskStatus start : [taskId] = {}", taskId);
        requireNotNull(taskId, "at taskId in DsmoqClient#getTaskStatus");
        return get("/api/tasks/" + taskId, JsonUtil::toTaskStatus);
    }

    /**
     * CSVファイルからAttributeを読み込む。
     *
     * POST /api/datasets/${dataset_id}/attributes/import を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param file AttributeをインポートするCSVファイル
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void importAttribute(String datasetId, File file) {
        logger.debug(LOG_MARKER, "DsmoqClient#importAttribute start : [datasetId] = {}, [file] = {}", datasetId,
                (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in DsmoqClient#importAttribute");
        requireNotNull(file, "at file in DsmoqClient#importAttribute");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            return builder.build();
        };
        post("/api/datasets/" + datasetId + "/attributes/import", entity, x -> x);
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。
     * 
     * PUT /api/datasets/${dataset_id}/images/featured を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setFeaturedImageToDataset(String datasetId, File file) {
        logger.debug(LOG_MARKER, "DsmoqClient#setFeaturedImageToDataset start : [datasetId] = {}, [file] = {}",
                datasetId, (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in DsmoqClient#setFeaturedImageToDataset");
        requireNotNull(file, "at file in DsmoqClient#setFeaturedImageToDataset");
        DatasetAddImages image = addImagesToDataset(datasetId, file);
        setFeaturedImageToDataset(datasetId, image.getImages().get(0).getId());
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。
     *
     * PUT /api/datasets/${dataset_id}/images/featured を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param imageId 指定する画像ID
     * @throws NullPointerException datasetId、imageIdのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setFeaturedImageToDataset(String datasetId, String imageId) {
        logger.debug(LOG_MARKER, "DsmoqClient#setFeaturedImageToDataset start : [datasetId] = {}, [imageId] = {}",
                datasetId, imageId);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#setFeaturedImageToDataset");
        requireNotNull(imageId, "at imageId in DsmoqClient#setFeaturedImageToDataset");
        put("/api/datasets/" + datasetId + "/images/featured",
                new SetFeaturedImageToDatasetParam(imageId).toJsonString(), x -> x);
    }

    /**
     * メンバーのロールを設定する。
     *
     * PUT /api/groups/${group_id}/members/${user_id} を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#setMemberRole start : [groupId] = {}, [userId] = {}, [param] = {}",
                groupId, userId, param);
        requireNotNull(groupId, "at groupId in DsmoqClient#setMemberRole");
        requireNotNull(userId, "at userId in DsmoqClient#setMemberRole");
        requireNotNull(param, "at param in DsmoqClient#setMemberRole");
        put("/api/groups/" + groupId + "/members/" + userId, param.toJsonString(), x -> x);
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。
     * 
     * PUT /api/datasets/${dataset_id}/image/primary を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     * @throws NullPointerException datasetId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToDataset(String datasetId, File file) {
        logger.debug(LOG_MARKER, "DsmoqClient#setPrimaryImageToDataset start : [datasetId] = {}, [file] = {}",
                datasetId, (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in DsmoqClient#setPrimaryImageToDataset");
        requireNotNull(file, "at file in DsmoqClient#setPrimaryImageToDataset");
        DatasetAddImages image = addImagesToDataset(datasetId, file);
        setPrimaryImageToDataset(datasetId, new SetPrimaryImageParam(image.getImages().get(0).getId()));
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。
     *
     * PUT /api/datasets/${dataset_id}/image/primary を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#setPrimaryImageToDataset start : [datasetId] = {}, [param] = {}",
                datasetId, param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#setPrimaryImageToDataset");
        requireNotNull(param, "at param in DsmoqClient#setPrimaryImageToDataset");
        put("/api/datasets/" + datasetId + "/images/primary", param.toJsonString(), x -> x);
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     * 
     * PUT /api/groups/${group_id}/images/primary を呼ぶ。
     * 
     * @param groupId グループID
     * @param file 画像ファイル
     * @throws NullPointerException groupId、fileのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToGroup(String groupId, File file) {
        logger.debug(LOG_MARKER, "DsmoqClient#setPrimaryImageToGroup start : [groupId] = {}, [file] = {}", groupId,
                (file == null) ? "null" : file.getName());
        requireNotNull(groupId, "at groupId in DsmoqClient#setPrimaryImageToGroup");
        requireNotNull(file, "at file in DsmoqClient#setPrimaryImageToGroup");
        GroupAddImages image = addImagesToGroup(groupId, file);
        setPrimaryImageToGroup(groupId, new SetPrimaryImageParam(image.getImages().get(0).getId()));
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     *
     * PUT /api/groups/${group_id}/images/primary を呼ぶ。
     * 
     * @param groupId グループID
     * @param param メイン画像指定情報
     * @throws NullPointerException groupId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#setPrimaryImageToGroup start : [groupId] = {}, [param] = {}", groupId,
                param);
        requireNotNull(groupId, "at groupId in DsmoqClient#setPrimaryImageToGroup");
        requireNotNull(param, "at param in DsmoqClient#setPrimaryImageToGroup");
        put("/api/groups/" + groupId + "/images/primary", param.toJsonString(), x -> x);
    }

    /**
     * データセットの情報を更新する。
     *
     * PUT /api/datasets/${dataset_id}/metadata を呼ぶ。
     * 
     * @param datasetId DatasetID
     * @param param データセット更新情報
     * @throws NullPointerException datasetId、paramのいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public void updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#updateDatasetMetaInfo start : [datasetId] = {}, [param] = {}", datasetId,
                param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#updateDatasetMetaInfo");
        requireNotNull(param, "at param in DsmoqClient#updateDatasetMetaInfo");
        put("/api/datasets/" + datasetId + "/metadata", param.toJsonString(), x -> x);
    }

    /**
     * ログインユーザのE-Mailを変更する。
     *
     * POST /api/profile/email_change_request を呼ぶ。
     * 
     * @param param E-Mail変更情報
     * @return プロファイル
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User updateEmail(UpdateEmailParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#updateEmail start : [param] = {}", param);
        requireNotNull(param, "at param in DsmoqClient#updateEmail");
        return post("/api/profile/email_change_requests", param.toJsonString(), JsonUtil::toUser);
    }

    /**
     * ファイルを更新する。
     *
     * POST /api/datasets/${dataset_id}/files/${file_id} を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#updateFile start : [datasetId] = {}, [fileId] = {}, [file] = {}",
                datasetId, fileId, (file == null) ? "null" : file.getName());
        requireNotNull(datasetId, "at datasetId in DsmoqClient#updateFile");
        requireNotNull(fileId, "at fileId in DsmoqClient#updateFile");
        requireNotNull(file, "at file in DsmoqClient#updateFile");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            return builder.build();
        };
        return post("/api/datasets/" + datasetId + "/files/" + fileId, entity, JsonUtil::toDatasetFile);
    }

    /**
     * ファイル情報を更新する。
     *
     * POST /api/datasets/${dataset_id}/files/${file_id}/metadata を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#updateFileMetaInfo start : [datasetId] = {}, [fileId] = {}, [param] = {}",
                datasetId, fileId, param);
        requireNotNull(datasetId, "at datasetId in DsmoqClient#updateFileMetaInfo");
        requireNotNull(fileId, "at fileId in DsmoqClient#updateFileMetaInfo");
        requireNotNull(param, "at param in DsmoqClient#updateFileMetaInfo");
        return put("/api/datasets/" + datasetId + "/files/" + fileId + "/metadata", param.toJsonString(),
                JsonUtil::toDatasetFile);
    }

    /**
     * グループ詳細情報を更新する。
     *
     * PUT /api/groups/${group_id} を呼ぶ。
     * 
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
        logger.debug(LOG_MARKER, "DsmoqClient#updateGroup start : [groupId] = {}, [param] = {}", groupId, param);
        requireNotNull(groupId, "at groupId in DsmoqClient#updateGroup");
        requireNotNull(param, "at param in DsmoqClient#updateGroup");
        return put("/api/groups/" + groupId, param.toJsonString(), JsonUtil::toGroup);
    }

    /**
     * ログインユーザのプロファイルを更新する。
     *
     * PUT /api/profile を呼ぶ。
     * 
     * @param param プロファイル更新情報
     * @return プロファイル
     * @throws NullPointerException paramがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User updateProfile(UpdateProfileParam param) {
        logger.debug(LOG_MARKER, "DsmoqClient#updateProfile start : [param] = {}", param);
        requireNotNull(param, "at param in DsmoqClient#updateProfile");
        return put("/api/profile", param.toJsonString(), JsonUtil::toUser);
    }

    /**
     * ログインユーザの画像を更新する。
     *
     * POST /api/profile/image を呼ぶ。
     * 
     * @param file 画像ファイル
     * @return プロファイル
     * @throws NullPointerException fileがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public User updateProfileIcon(File file) {
        logger.debug(LOG_MARKER, "DsmoqClient#updateProfileIcon start : [file] = {}",
                (file == null) ? "null" : file.getName());
        requireNotNull(file, "at file in DsmoqClient#updateProfileIcon");
        Supplier<HttpEntity> entity = () -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("icon", file);
            return builder.build();
        };
        return post("/api/profile/image", entity, JsonUtil::toUser);
    }

    /**
     * Authorizationヘッダを追加する。
     * 
     * @param request リクエストオブジェクト
     */
    private void addAuthorizationHeader(HttpUriRequest request) {
        if (!_apiKey.isEmpty() && !_secretKey.isEmpty()) {
            request.addHeader(AUTHORIZATION_HEADER_NAME,
                    String.format("api_key=%s, signature=%s", _apiKey, getSignature(_apiKey, _secretKey)));
        }
    }

    /**
     * HTTPクライアントを取得する。
     * 
     * @return HTTPクライアント
     */
    private AutoCloseHttpClient createHttpClient() {
        return new AutoCloseHttpClient();
    }

    /**
     * DELETEリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T delete(String url, Function<String, T> responseFunc) {
        return send(() -> new AutoHttpDelete(_baseUrl + url), null,
                (HttpResponse response) -> responseFunc.apply(responseToString(response)));
    }

    /**
     * リクエストを実行する。
     * 
     * @param <T> レスポンス変換後の型
     * @param request リクエスト
     * @param responseFunc レスポンス変換関数
     * @return 変換結果
     * @throws IOException 接続に失敗した場合
     * @throws HttpException レスポンスがHTTPレスポンスとして不正な場合
     * @throws ErrorRespondedException エラーレスポンスが返ってきた場合
     */
    private <T> T execute(HttpUriRequest request, ResponseFunction<T> responseFunc)
            throws IOException, HttpException, ErrorRespondedException {
        try (AutoCloseHttpClient client = createHttpClient()) {
            HttpResponse response = client.execute(request);
            if (isErrorStatus(response.getStatusLine().getStatusCode())) {
                throw new ErrorRespondedException(response);
            }
            return responseFunc.apply(response);
        }
    }

    /**
     * GETリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param ext リクエストに対する追加処理
     * @param responseFunc レスポンス変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, Consumer<AutoHttpGet> ext, ResponseFunction<T> responseFunc) {
        return send(() -> new AutoHttpGet(_baseUrl + url), ext, responseFunc);
    }

    /**
     * GETリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, Function<String, T> responseFunc) {
        return get(url, (HttpResponse response) -> responseFunc.apply(responseToString(response)));
    }

    /**
     * GETリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param responseFunc レスポンス変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, ResponseFunction<T> responseFunc) {
        return get(url, null, responseFunc);
    }

    /**
     * GETリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param jsonParam リクエストに付与するJSONパラメータ
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T get(String url, String jsonParam, Function<String, T> responseFunc) {
        return send(
                () -> new AutoHttpGet(_baseUrl + url + "?" + REQUEST_JSON_PARAM_NAME + "="
                        + URLEncoder.encode(jsonParam, DEFAULT_REQUEST_CHARSET.name())),
                null, (HttpResponse response) -> responseFunc.apply(responseToString(response)));
    }

    /**
     * 指定されたHttpResponseのHeader部から、ファイル名を取得する。
     * 
     * @param response HTTPレスポンスオブジェクト
     * @return ファイル名、取得できなかった場合null
     */
    private String getFileNameFromHeader(HttpResponse response) {
        Header header = response.getFirstHeader(CONTENT_DISPOSITION_HEADER_NAME);
        if (header == null) {
            logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_CONTENT_DISPOSITION_NOT_FOUND));
            return null;
        }
        Matcher m = COTENT_DISPOSITION_PATTERN.matcher(header.getValue());
        if (!m.find()) {
            logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_ILLEGAL_FORMAT_CONTENT_DISPOSITION),
                    header.getValue());
            return null;
        }
        String rawCharset = m.group(COTENT_DISPOSITION_PATTERN_CHARSET);
        String rawFileName = m.group(COTENT_DISPOSITION_PATTERN_FILENAME);
        try {
            Charset charset = Charset.forName(rawCharset);
            return URLDecoder.decode(rawFileName, charset.name());
        } catch (UnsupportedEncodingException | IllegalCharsetNameException | UnsupportedCharsetException e) {
            logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_UNSUPPORTED_CHARSET), rawCharset, e);
            return null;
        }
    }

    /**
     * 認証文字列を作成する。
     * 
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     * @return 作成した認証文字列
     */
    private String getSignature(String apiKey, String secretKey) {
        try {
            SecretKeySpec sk = new SecretKeySpec(secretKey.getBytes(DEFAULT_REQUEST_CHARSET), HASH_ALGORITHM);
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(sk);
            byte[] result = mac.doFinal((apiKey + "&" + secretKey).getBytes(DEFAULT_REQUEST_CHARSET));
            return URLEncoder.encode(Base64.getEncoder().encodeToString(result), DEFAULT_REQUEST_CHARSET.name());
        } catch (Exception e) {
            logger.error(LOG_MARKER, resource.getString(ResourceNames.LOG_ERROR_OCCURED), e.getMessage());
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * HEADリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param responseFunc レスポンス変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T head(String url, ResponseFunction<T> responseFunc) {
        return send(() -> new AutoHttpHead(_baseUrl + url), null, responseFunc);
    }

    /**
     * POSTリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param ext リクエストに対する追加処理
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, Consumer<AutoHttpPost> ext, Function<String, T> responseFunc) {
        return send(() -> new AutoHttpPost(_baseUrl + url), ext,
                (HttpResponse response) -> responseFunc.apply(responseToString(response)));
    }

    /**
     * POSTリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, Function<String, T> responseFunc) {
        return post(url, (Supplier<HttpEntity>) null, responseFunc);
    }

    /**
     * POSTリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param jsonParam リクエストボディに付与するJSONパラメータ
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, String jsonParam, Function<String, T> responseFunc) {
        return post(url, () -> toHttpEntity(jsonParam), responseFunc);
    }

    /**
     * POSTリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param entity リクエストボディのサプライヤ
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T post(String url, Supplier<HttpEntity> entity, Function<String, T> responseFunc) {
        Consumer<AutoHttpPost> ext = entity == null ? null : req -> {
            req.setEntity(entity.get());
        };
        return post(url, ext, responseFunc);
    }

    /**
     * PUTリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param ext リクエストに対する追加処理
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T put(String url, Consumer<AutoHttpPut> ext, Function<String, T> responseFunc) {
        return send(() -> new AutoHttpPut(_baseUrl + url), ext,
                (HttpResponse response) -> responseFunc.apply(responseToString(response)));
    }

    /**
     * PUTリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param jsonParam リクエストボディに付与するJSONパラメータ
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T put(String url, String jsonParam, Function<String, T> responseFunc) {
        return put(url, () -> toHttpEntity(jsonParam), responseFunc);
    }

    /**
     * PUTリクエストを送信する。
     * 
     * @param url 送信先URL
     * @param entity リクエストボディのサプライヤ
     * @param responseFunc レスポンスボディ変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T> T put(String url, Supplier<HttpEntity> entity, Function<String, T> responseFunc) {
        Consumer<AutoHttpPut> ext = entity == null ? null : req -> {
            req.setEntity(entity.get());
        };
        return put(url, ext, responseFunc);
    }

    /**
     * レスポンスからレスポンスボディの文字列表現を取得する。
     *
     * レスポンスヘッダに文字コード指定がない場合、デフォルトの文字コード(UTF-8)を使用する。
     * 
     * @param response レスポンス
     * @return レスポンスボディの文字列表現
     */
    private String responseToString(HttpResponse response) throws IOException {
        return responseToString(response, DEFAULT_RESPONSE_CHARSET.name());
    }

    /**
     * レスポンスからレスポンスボディの文字列表現を取得する。
     * 
     * @param response レスポンス
     * @param charset レスポンスヘッダに文字コード指定がない場合に使用する文字コード
     * @return レスポンスボディの文字列表現
     */
    private String responseToString(HttpResponse response, String charset) throws IOException {
        String str = EntityUtils.toString(response.getEntity(), charset);
        logger.debug(LOG_MARKER, "response body = {}", str);
        return str;
    }

    /**
     * リクエストを送信する。
     * 
     * @param request リクエストのサプライヤ
     * @param ext リクエストに対する追加処理
     * @param responseFunc レスポンス変換関数
     * @return 変換結果
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    private <T extends HttpUriRequest & AutoCloseable, R> R send(ExceptionSupplier<T> request, Consumer<T> ext,
            ResponseFunction<R> responseFunc) {
        try (T req = request.get()) {
            addAuthorizationHeader(req);
            if (ext != null) {
                ext.accept(req);
            }
            return execute(req, responseFunc);
        } catch (Exception e) {
            // 内部で発生した例外を、公開用の非検査例外に翻訳する
            throw translateInnerException(e);
        }
    }

    /**
     * 内部で送出される例外を、公開用に翻訳する。
     * 
     * @param e 内部で送出された例外
     * @return 公開用に翻訳された例外
     */
    private RuntimeException translateInnerException(Exception e) {
        logger.error(LOG_MARKER, resource.getString(ResourceNames.LOG_ERROR_OCCURED), e.getMessage());
        if (e instanceof ErrorRespondedException) {
            // ErrorRespondedExceptionなら、HttpStatusExceptionに変換する
            ErrorRespondedException ex = (ErrorRespondedException) e;
            return new HttpStatusException(ex.getStatusCode(), ex.getBody(), ex);
        }
        if (e instanceof SocketTimeoutException) {
            // SocketTimeoutExceptionなら、TimeoutExceptionに変換する
            return new TimeoutException(e.getMessage(), e);
        }
        if (e instanceof HttpHostConnectException) {
            // HttpHostConnectExceptionなら、ConnectionLostExceptionに変換する
            return new ConnectionLostException(e.getMessage(), e);
        }
        // 上記以外の例外なら、ApiFailedExceptionに変換する
        return new ApiFailedException(e.getMessage(), e);
    }
}
