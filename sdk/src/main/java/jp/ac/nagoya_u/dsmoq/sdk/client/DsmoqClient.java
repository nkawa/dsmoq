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
import jp.ac.nagoya_u.dsmoq.sdk.util.DsmoqHttpException;
import jp.ac.nagoya_u.dsmoq.sdk.util.ErrorRespondedException;
import jp.ac.nagoya_u.dsmoq.sdk.util.HttpStatusException;
import jp.ac.nagoya_u.dsmoq.sdk.util.JsonUtil;
import jp.ac.nagoya_u.dsmoq.sdk.util.ResponseFunction;
import jp.ac.nagoya_u.dsmoq.sdk.util.TimeoutException;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpRequestBase;
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
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
    public DsmoqClient(String baseUrl, String apiKey, String secretKey) {
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/datasets?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasets(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s", datasetId))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toDataset(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDataset
     * @throws NullPointerException files、あるいはfilesの要素のいずれかがnullの場合
     * @throws HttpStatusException エラーレスポンスが返ってきた場合
     * @throws TimeoutException 接続がタイムアウトした場合
     * @throws ConnectionLostException 接続が失敗した、または失われた場合
     * @throws ApiFailedException 上記以外の何らかの例外が発生した場合
     */
    public Dataset createDataset(boolean saveLocal, boolean saveS3, File... files) {
        requireNotNull(files, "at files in createDataset");
        requireNotNullAll(files, "at files[%d] in createDataset");
        logger.debug(LOG_MARKER, "createDataset start : [saveLocal] = {}, [saveS3] = {}, [files size] = {}", saveLocal, saveS3, (files != null) ? files.length : "null");
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // MultipartEntityBuilderのモード互換モードを設定
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            // MultipartEntityBuilderの文字コードにutf-8を設定
            builder.setCharset(StandardCharsets.UTF_8);
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("file[]", file));
            builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
            builder.addTextBody("saveS3", saveS3 ? "true" : "false");
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            logger.debug(LOG_MARKER, "createDataset end : receive json = {}", json);
            return JsonUtil.toDataset(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        requireNotNull(name, "at name in createDataset");
        logger.debug(LOG_MARKER, "createDataset start : [name] = {}, [saveLocal] = {}, [saveS3] = {}", name, saveLocal, saveS3);
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("name", name, TEXT_PLAIN_UTF8);
            builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
            builder.addTextBody("saveS3", saveS3 ? "true" : "false");
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            logger.debug(LOG_MARKER, "createDataset end : receive json = {}", json);
            return JsonUtil.toDataset(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            addAuthorizationHeader(request);
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
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            logger.debug(LOG_MARKER, "createDataset end : receive json = {}", json);
            return JsonUtil.toDataset(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/files", datasetId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // MultipartEntityBuilderのモード互換モードを設定
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            // MultipartEntityBuilderの文字コードにutf-8を設定
            builder.setCharset(StandardCharsets.UTF_8);
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("files", file));
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            logger.debug(LOG_MARKER, "addFiles end : receive json = {}", json);
            return JsonUtil.toDatasetAddFiles(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetFile(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + String.format("/api/datasets/%s/files/%s/metadata", datasetId, fileId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetFile(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/metadata", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/images", datasetId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetAddImages(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/images/primary", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s/images/%s", datasetId, imageId))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetDeleteImage(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s/images?d=", datasetId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetGetImage(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s/acl?d=", datasetId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetOwnership(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/acl", datasetId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> p = new ArrayList<>();
            p.add(new BasicNameValuePair("d", SetAccessLevelParam.toJsonString(params)));
            request.setEntity(new UrlEncodedFormEntity(p, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetOwnerships(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/guest_access", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s", datasetId))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/storage", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetTask(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + String.format("/api/datasets/%s/copy", datasetId))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toCopiedDataset(json).getDatasetId();
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/attributes/import", datasetId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        requireNotNull(datasetId, "at datasetId in exportAttribute");
        requireNotNull(f, "at f in exportAttribute");
        String url = _baseUrl + String.format("/api/datasets/%s/attributes/export", datasetId);
        logger.debug(LOG_MARKER, "exportAttribute start : [url] = {}", url);
        try (AutoHttpGet request = new AutoHttpGet(url)){
            addAuthorizationHeader(request);
            return execute(request, response -> {
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
        } catch (Exception e) {
            throw translateInnerException(e);
        }
    }

    /**
     * データセットに一覧で表示するFeatured Dataset画像を設定する。（PUT /api/datasets/${dataset_id}/image/${image_id}/featured相当）
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
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/images/%s/featured", datasetId, imageId))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpHead request = new AutoHttpHead(String.format("%s/files/%s/%s", _baseUrl, datasetId, fileId))) {
            addAuthorizationHeader(request);
            return execute(request, response -> {
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
        } catch (Exception e) {
            throw translateInnerException(e);
        }
    }

    /**
     * データセットからファイルをダウンロードする。（GET /files/${dataset_id}/${file_id}相当）
     * @param <T> ファイルデータ処理後の型
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param f ファイルデータを処理する関数 (引数のDatasetFileはこの処理関数中でのみ利用可能)
     * @return fの処理結果
     * @throws NullPointerException datasetIdまたはfileIdまたはfがnullの場合
     * @throws DsmoqHttpException ファイルの取得に失敗した場合
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
     * @throws DsmoqHttpException ファイルの取得に失敗した場合
     */
    public <T> T downloadFileWithRange(String datasetId, String fileId, Long from, Long to, Function<DatasetFileContent, T> f) {
        requireNotNull(datasetId, "at datasetId in downloadFileWithRange");
        requireNotNull(fileId, "at fileId in downloadFileWithRange");
        requireNotNull(f, "at f in downloadFileWithRange");
        requireGreaterOrEqualOrNull(from, 0L, "at from in downloadFileWithRange");
        requireGreaterOrEqualOrNull(to, 0L, "at to in downloadFileWithRange");
        String url = _baseUrl + String.format("/files/%s/%s", datasetId, fileId);
        logger.debug(LOG_MARKER, "downloadFileWithRange start : [downloadUrl] = {}, [from:to] = {}:{}", url, from, to);
        try (AutoHttpGet request = new AutoHttpGet(url)){
            addAuthorizationHeader(request);
            if (from != null || to != null) {
                request.setHeader("Range", String.format("bytes=%s-%s", from == null ? "" : from.toString(), to == null ? "" : to.toString()));
            }
            return execute(request, response -> {
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
            });
        } catch (Exception e) {
            logger.error(LOG_MARKER, "Error occured. [message]:{}", e.getMessage());
            throw new DsmoqHttpException(e.getMessage(), e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s/files?d=", datasetId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetFiles(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s/files/%s/zippedfiles?d=", datasetId, fileId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toDatasetZippedFiles(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/groups?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toGroups(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s", groupId))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toGroup(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s/members?d=%s", groupId, URLEncoder.encode(param.toJsonString(), "UTF-8")))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toMembers(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + "/api/groups")) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            return JsonUtil.toGroup(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/groups/%s", groupId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            return JsonUtil.toGroup(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s/images?d=", groupId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toGroupGetImage(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/images", groupId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            return JsonUtil.toGroupAddImages(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/groups/%s/images/primary", groupId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/groups/%s/images/%s", groupId, imageId))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toGroupDeleteImage(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
    }

    /**
     * グループにメンバーを追加する。（POST /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param params メンバー追加情報
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/members", groupId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> requestParams = new ArrayList<>();
            requestParams.add(new BasicNameValuePair("d", AddMemberParam.toJsonString(params)));
            request.setEntity(new UrlEncodedFormEntity(requestParams, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s", groupId)))) {
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/profile")){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toUser(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile"))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            return JsonUtil.toUser(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/image"))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("icon", file);
            request.setEntity(builder.build());
            String json = executeWithStringResponse(request);
            return JsonUtil.toUser(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/email_change_requests"))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            return JsonUtil.toUser(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile/password"))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = executeWithStringResponse(request);
            JsonUtil.statusCheck(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/accounts")){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toUsers(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/licenses")){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toLicenses(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/tasks/%s", taskId))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toTaskStatus(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
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
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/statistics?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = executeWithStringResponse(request);
            return JsonUtil.toStatistics(json);
        } catch (Exception e) {
            throw translateInnerException(e);
        }
    }

    /**
     * Authorizationヘッダを追加する。
     * @param request リクエストオブジェクト
     */
    void addAuthorizationHeader(HttpRequestBase request) {
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
            logger.error(LOG_MARKER, "Error occured. [message]:{}", e.getMessage());
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * HTTPクライアントを取得する。
     * @return HTTPクライアント
     */
    private AutoCloseHttpClient createHttpClient() {
        return new AutoCloseHttpClient();
    }

    /** デフォルトのレスポンスボディ文字コード */
    public static final Charset DEFAULT_RESPONSE_CHAESET = StandardCharsets.UTF_8;
    
    /**
     * リクエストを実行し、文字列型のレスポンスを取得する。
     * @param request リクエスト
     * @return レスポンスボディ文字列
     * @throws IOException 接続に失敗した場合
     * @throws HttpException レスポンスがHTTPレスポンスとして不正な場合 
     * @throws ErrorRespondedException エラーレスポンスが返ってきた場合
     */
    private String executeWithStringResponse(HttpUriRequest request) throws IOException, HttpException, ErrorRespondedException {
        return executeWithStringResponse(request, DEFAULT_RESPONSE_CHAESET.name());
    }

    /**
     * リクエストを実行し、文字列型のレスポンスを取得する。
     * @param request リクエスト
     * @param charset レスポンスヘッダに文字コード指定がない場合に使用する文字コード
     * @return レスポンスボディ文字列
     * @throws IOException 接続に失敗した場合
     * @throws HttpException レスポンスがHTTPレスポンスとして不正な場合 
     * @throws ErrorRespondedException エラーレスポンスが返ってきた場合
     */
    private String executeWithStringResponse(HttpUriRequest request, String charset) throws IOException, HttpException, ErrorRespondedException {
        return execute(request, response -> EntityUtils.toString(response.getEntity(), charset));
    }

    /**
     * リクエストを実行する。
     * @param <T> レスポンス変換後の型
     * @param request リクエスト
     * @param f レスポンスボディ変換関数
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
     * 内部で送出される例外を、公開用に翻訳する。
     * @param e 内部で送出される例外
     * @return 公開用に翻訳された例外
     */
    private static RuntimeException translateInnerException(Exception e) {
        logger.error(LOG_MARKER, "Error occured. [message]:{}", e.getMessage());
        if (e instanceof ErrorRespondedException) {
            return new HttpStatusException(((ErrorRespondedException) e).getStatusCode());
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
