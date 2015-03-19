package jp.ac.nagoya_u.dsmoq.sdk.client;

import jp.ac.nagoya_u.dsmoq.sdk.http.*;
import jp.ac.nagoya_u.dsmoq.sdk.request.*;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;
import jp.ac.nagoya_u.dsmoq.sdk.util.*;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoCloseHttpClient;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpDelete;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpGet;
import jp.ac.nagoya_u.dsmoq.sdk.http.AutoHttpPut;
import jp.ac.nagoya_u.dsmoq.sdk.util.ConnectionLostException;
import jp.ac.nagoya_u.dsmoq.sdk.util.NotFoundException;
import jp.ac.nagoya_u.dsmoq.sdk.util.TimeoutException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.http.conn.HttpHostConnectException;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.file.Paths;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.stream.Collectors;

/**
 * dsmoq APIを叩くためのクライアントクラス
 * 個々のAPIとの対比はJavaDocとAPIのドキュメントを比較してみてください。
 */
public class DsmoqClient {
    private String _baseUrl;
    private String _apiKey;
    private String _secretKey;

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
     */
    public RangeSlice<DatasetsSummary> getDatasets(GetDatasetsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/datasets?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toDatasets(json);
        } catch(UnsupportedEncodingException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * Datasetを取得する。(GET /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @return 取得結果
     */
    public Dataset getDataset(String datasetId) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s", datasetId))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toDataset(json);
        }
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDataset
     */
    public Dataset createDataset(boolean saveLocal, boolean saveS3, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("file[]", file));
            builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
            builder.addTextBody("saveS3", saveS3 ? "true" : "false");
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toDataset(json);
        }
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @return 作成したDataset
     */
    public Dataset createDataset(String name, boolean saveLocal, boolean saveS3) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("name", name);
            builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
            builder.addTextBody("saveS3", saveS3 ? "true" : "false");
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toDataset(json);
        }
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param name データセットの名前
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDataset
     */
    public Dataset createDataset(String name, boolean saveLocal, boolean saveS3, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("name", name);
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("file[]", file));
            builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
            builder.addTextBody("saveS3", saveS3 ? "true" : "false");
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toDataset(json);
        }
    }

    /**
     * Datasetにファイルを追加する。(POST /api/datasets/${dataset_id}/files相当)
     * @param datasetId DatasetID
     * @param files Datasetに追加するファイル(複数可)
     * @return 追加したファイルの情報
     */
    public DatasetAddFiles addFiles(String datasetId, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/files", datasetId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("files", file));
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toDatasetAddFiles(json);
        }
    }

    /**
     * ファイルを更新する。(POST /api/datasets/${dataset_id}/files/${file_id}相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param file 更新対象のファイル
     * @return 更新されたファイル情報
     */
    public DatasetFile updateFile(String datasetId, String fileId, File file) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toDatasetFile(json);
        }
    }

    /**
     * ファイル情報を更新する。(POST /api/datasets/${dataset_id}/files/${file_id}/metadata相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param param ファイル更新情報
     * @return 更新したファイル情報
     */
    public DatasetFile updateFileMetaInfo(String datasetId, String fileId, UpdateFileMetaParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + String.format("/api/datasets/%s/files/%s/metadata", datasetId, fileId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            return JsonUtil.toDatasetFile(json);
        }
    }

    /**
     * データセットからファイルを削除する。（DELETE /api/datasets/${dataset_id}/files/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     */
    public void deleteFile(String datasetId, String fileId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            addAuthorizationHeader(request);
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * データセットの情報を更新する。(PUT /api/datasets.${dataset_id}/metadata相当)
     * @param datasetId DatasetID
     * @param param データセット更新情報
     */
    public void updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/metadata", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * データセットに画像を追加する。（POST /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param files 追加する画像ファイル
     * @return 追加した画像情報
     */
    public DatasetAddImages addImagesToDataset(String datasetId, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/images", datasetId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toDatasetAddImages(json);
        }
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。（PUT /api/datasets/${dataset_id}/image/primary相当）
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     */
    public void setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/images/primary", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。
     * @param datasetId DatasetID
     * @param file 追加する画像ファイル
     */
    public void setPrimaryImageToDataset(String datasetId, File file) {
        DatasetAddImages image = addImagesToDataset(datasetId, file);
        setPrimaryImageToDataset(datasetId, new SetPrimaryImageParam(image.getImages().get(0).getId()));
    }

    /**
     * データセットから画像を削除する。（DELETE /api/datasets/${dataset_id}/image/${image_id}相当）
     * @param datasetId DatasetID
     * @param imageId 画像ID
     * @return 画像削除後のデータセットのメイン画像情報
     */
    public DatasetDeleteImage deleteImageToDataset(String datasetId, String imageId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s/images/%s", datasetId, imageId))) {
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toDatasetDeleteImage(json);
        }
    }

    /**
     * データセットの画像一覧を取得する。（GET /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットの画像一覧
     */
    public RangeSlice<DatasetGetImage> getDatasetImage(String datasetId, GetRangeParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s/images?d=", datasetId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toDatasetGetImage(json);
        } catch(UnsupportedEncodingException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットのアクセス権一覧を取得する。（GET /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param param 一覧取得情報
     * @return データセットのアクセス権一覧
     */
    public RangeSlice<DatasetOwnership> getAccessLevel(String datasetId, GetRangeParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s/acl?d=", datasetId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toDatasetOwnership(json);
        } catch(UnsupportedEncodingException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットのアクセス権を変更する。（POST /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param params アクセス権制御情報
     * @return 変更後のアクセス権情報
     */
    public DatasetOwnerships changeAccessLevel(String datasetId, List<SetAccessLevelParam> params) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/acl", datasetId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> p = new ArrayList<>();
            p.add(new BasicNameValuePair("d", SetAccessLevelParam.toJsonString(params)));
            request.setEntity(new UrlEncodedFormEntity(p, StandardCharsets.UTF_8));
            String json = execute(request);
            return JsonUtil.toDatasetOwnerships(json);
        }
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。（PUT /api/datasets/${dataset_id}/guest_access相当）
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     */
    public void changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/guest_access", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * データセットを削除する。(DELETE /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     */
    public void deleteDataset(String datasetId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s", datasetId))) {
            addAuthorizationHeader(request);
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * データセットの保存先を変更する。(PUT /api/datasets/${dataset_id}/storage相当)
     * @param datasetId DatasetID
     * @param param 保存先変更情報
     * @return 変更タスクの情報
     */
    public DatasetTask changeDatasetStorage(String datasetId, ChangeStorageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/storage", datasetId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            return JsonUtil.toDatasetTask(json);
        }
    }

    /**
     * データセットをコピーします。（POST /api/datasets/${dataset_id}/copy相当）
     * @param datasetId DatasetID
     * @return コピーしたDatasetID
     */
    public String copyDataset(String datasetId) {
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + String.format("/api/datasets/%s/copy", datasetId))) {
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toCopiedDataset(json).getDatasetId();
        }
    }

    /**
     * CSVファイルからAttributeを読み込む。（POST /api/datasets/${dataset_id}/attributes/import相当）
     * @param datasetId DatasetID
     * @param file AttributeをインポートするCSVファイル
     */
    public void importAttribute(String datasetId, File file) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/attributes/import", datasetId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            request.setEntity(builder.build());
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * CSVファイルにAttributeを出力する。（GET /api/datasets/${dataset_id}/attributes/export相当）
     * @param datasetId DatasetID
     * @param downloadDirectory 出力先ディレクトリ
     * @return CSVファイル
     */
    public File exportAttribute(String datasetId, String downloadDirectory) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/datasets/%s/attributes/export", datasetId))){
            addAuthorizationHeader(request);

            File file = Paths.get(downloadDirectory, "export.csv").toFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                executeWrite(request, fos);
            }
            return file;
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットからファイルをダウンロードする。（GET /files/${dataset_id}/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル情報
     */
    public File downloadFile(String datasetId, String fileId, String downloadDirectory) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/files/%s/%s", datasetId, fileId))){
            addAuthorizationHeader(request);

            Dataset dataset = getDataset(datasetId);
            Optional<String> targetName = dataset.getFiles().stream().filter(x -> x.getId().equals(fileId)).map(x -> x.getName()).findFirst();

            if (! targetName.isPresent()) {
                targetName = dataset.getFiles().stream().flatMap(x -> x.getZipedFiles().stream()).filter(x -> x.getId().equals(fileId)).map(x -> x.getName()).findFirst();
                if (! targetName.isPresent()) {
                    throw new NotFoundException();
                }
            }

            File file = Paths.get(downloadDirectory, fileId + "_" + targetName.get()).toFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                executeWrite(request, fos);
            }
            return file;
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットから一時ディレクトリにファイルをダウンロードする。
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return ダウンロードしたファイル
     */
    public File downloadFile(String datasetId, String fileId) {
        File temp = new File("temp");
        if (! temp.exists()) temp.mkdir();
        return downloadFile(datasetId, fileId, "temp");
    }

    /**
     * データセットからすべてのファイルをダウンロードする。
     * @param datasetId DatasetID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllFiles(String datasetId, String downloadDirectory) {
        Dataset dataset = getDataset(datasetId);
        List<DatasetFile> datasetFiles = dataset.getFiles();
        return datasetFiles.stream().map(file -> downloadFile(datasetId, file.getId(), downloadDirectory)).collect(Collectors.toList());
    }

    /**
     * データセットから一時ディレクトリにすべてのファイルをダウンロードする。
     * @param datasetId DatasetID
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllFiles(String datasetId) {
        File temp = new File("temp");
        if (! temp.exists()) temp.mkdir();
        return downloadAllFiles(datasetId, "temp");
    }

    /**
     * データセットからすべてのファイルをダウンロードする。Zipファイルは圧縮されたファイルのみを取得する。
     * @param datasetId DatasetID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllExpandedFiles(String datasetId, String downloadDirectory) {
        Dataset dataset = getDataset(datasetId);
        List<String> datasetFiles = dataset.getFiles().stream().filter(f -> ! f.isZip()).map(f -> f.getId()).collect(Collectors.toList());
        List<String> zipedFiles = dataset.getFiles().stream().flatMap(f -> f.getZipedFiles().stream()).map(f -> f.getId()).collect(Collectors.toList());
        datasetFiles.addAll(zipedFiles);
        return datasetFiles.stream().map(id -> downloadFile(datasetId, id, downloadDirectory)).collect(Collectors.toList());
    }

    /**
     * データセットから一時ディレクトリにすべてのファイルをダウンロードする。Zipファイルは圧縮されたファイルのみを取得する。
     * @param datasetId DatasetID
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllExpandedFiles(String datasetId) {
        File temp = new File("temp");
        if (! temp.exists()) temp.mkdir();
        return downloadAllExpandedFiles(datasetId, "temp");
    }

    /**
     * グループ一覧を取得する。（GET /api/groups相当）
     * @param param グループ一覧取得情報
     * @return グループ一覧情報
     */
    public RangeSlice<GroupsSummary> getGroups(GetGroupsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/groups?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toGroups(json);
        } catch (UnsupportedEncodingException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループ詳細を取得する。（GET /api/groups/${group_id}相当）
     * @param groupId グループID
     * @return グループ詳細情報
     */
    public Group getGroup(String groupId) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s", groupId))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toGroup(json);
        }
    }

    /**
     * グループのメンバー一覧を取得する。（GET /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param グループメンバー一覧取得情報
     * @return グループメンバー一覧情報
     */
    public RangeSlice<MemberSummary> getMembers(String groupId, GetMembersParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s/members?d=%s", groupId, URLEncoder.encode(param.toJsonString(), "UTF-8")))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toMembers(json);
        } catch (UnsupportedEncodingException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループを作成する。（POST /api/groups相当）
     * @param param グループ作成情報
     * @return 作成したグループ詳細情報
     */
    public Group createGroup(CreateGroupParam param) {
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + "/api/groups")) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            return JsonUtil.toGroup(json);
        }
    }

    /**
     * グループ詳細情報を更新する。（PUT /api/groups/${group_id}相当）
     * @param groupId グループID
     * @param param グループ詳細更新情報
     * @return グループ詳細情報
     */
    public Group updateGroup(String groupId, UpdateGroupParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/groups/%s", groupId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            return JsonUtil.toGroup(json);
        }
    }

    /**
     * グループの画像一覧を取得する。（GET /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param param 一覧取得情報
     * @return グループの画像一覧情報
     */
    public RangeSlice<GroupGetImage> getGroupImage(String groupId, GetRangeParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s/images?d=", groupId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toGroupGetImage(json);
        } catch (UnsupportedEncodingException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループに画像を追加する。（POST /api/groups/${group_id}/images相当）
     * @param groupId グループID
     * @param files 画像ファイル
     * @return 追加した画像ファイル情報
     */
    public GroupAddImages addImagesToGroup(String groupId, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/images", groupId)))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toGroupAddImages(json);
        }
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。（PUT /api/groups/${group_id}/images/primary相当）
     * @param groupId グループID
     * @param param メイン画像指定情報
     */
    public void setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/groups/%s/images/primary", groupId))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。
     * @param groupId グループID
     * @param file 画像ファイル
     */
    public void setPrimaryImageToGroup(String groupId, File file) {
        GroupAddImages image = addImagesToGroup(groupId, file);
        setPrimaryImageToGroup(groupId, new SetPrimaryImageParam(image.getImages().get(0).getId()));
    }

    /**
     * グループから画像を削除する。（DELETE /api/groups/${group_id}/images/${image_id}相当）
     * @param groupId グループID
     * @param imageId 画像ID
     * @return 画像削除後のグループのメイン画像情報
     */
    public GroupDeleteImage deleteImageToGroup(String groupId, String imageId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/groups/%s/images/%s", groupId, imageId))) {
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toGroupDeleteImage(json);
        }
    }

    /**
     * グループにメンバーを追加する。（POST /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param メンバー追加情報
     */
    public void addMember(String groupId, List<AddMemberParam> param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/members", groupId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", AddMemberParam.toJsonString(param)));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * メンバーのロールを設定する。（PUT /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     * @param param ロール設定情報
     */
    public void setMemberRole(String groupId, String userId, SetMemberRoleParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * メンバーを削除する。（DELETE /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     */
    public void deleteMember(String groupId, String userId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            addAuthorizationHeader(request);
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * グループを削除する。（DELETE /api/groups/${group_id}相当）
     * @param groupId グループID
     */
    public void deleteGroup(String groupId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s", groupId)))) {
            addAuthorizationHeader(request);
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * ログインユーザのプロファイルを取得する。（GET /api/profile相当）
     * @return プロファイル
     */
    public User getProfile() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/profile")){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toUser(json);
        }
    }

    /**
     * ログインユーザのプロファイルを更新する。（PUT /api/profile相当）
     * @param param プロファイル更新情報
     * @return プロファイル
     */
    public User updateProfile(UpdateProfileParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile"))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            return JsonUtil.toUser(json);
        }
    }

    /**
     * ログインユーザの画像を更新する。（POST /api/profile/image相当）
     * @param file 画像ファイル
     * @return プロファイル
     */
    public User updateProfileIcon(File file) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/image"))) {
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("icon", file);
            request.setEntity(builder.build());
            String json = execute(request);
            return JsonUtil.toUser(json);
        }
    }

    /**
     * ログインユーザのE-Mailを変更する。（POST /api/profile/email_change_request相当）
     * @param param E-Mail変更情報
     * @return プロファイル
     */
    public User updateEmail(UpdateEmailParam param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/email_change_requests"))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            return JsonUtil.toUser(json);
        }
    }

    /**
     * ログインユーザのパスワードを変更する。（PUT /api/profile/password相当）
     * @param param パスワード変更情報
     */
    public void changePassword(ChangePasswordParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile/password"))) {
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            String json = execute(request);
            JsonUtil.statusCheck(json);
        }
    }

    /**
     * ユーザー一覧を取得する。（GET /api/accounts相当）
     * @return ユーザー一覧
     */
    public List<User> getAccounts() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/accounts")){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toUsers(json);
        }
    }

    /**
     * ライセンス一覧を取得する。（GET /api/licenses相当）
     * @return ライセンス一覧情報
     */
    public List<License> getLicenses() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/licenses")){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toLicenses(json);
        }
    }

    /**
     * タスクの現在のステータスを取得する。（GET /api/tasks/${task_id}相当）
     * @param taskId タスクID
     * @return タスクのステータス情報
     */
    public TaskStatus getTaskStatus(String taskId) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/tasks/%s", taskId))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toTaskStatus(json);
        }
    }

    /**
     * 統計情報を取得します。（GET /api/statistics相当）
     * @param param 統計情報期間指定
     * @return 統計情報
     */
    public List<StatisticsDetail> getStatistics(StatisticsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/statistics?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            addAuthorizationHeader(request);
            String json = execute(request);
            return JsonUtil.toStatistics(json);
        } catch(UnsupportedEncodingException e) {
            throw new ApiFailedException(e.getMessage(), e);
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

    /**
     * リクエストを実行する。
     * @param request リクエスト
     * @return 実行結果のJSON
     */
    private String execute(HttpUriRequest request) {
        try (AutoCloseHttpClient client = createHttpClient()) {
            HttpResponse response = client.execute(request);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 400) {
                throw new HttpStatusException(status);
            }
            return EntityUtils.toString(response.getEntity());
        } catch (SocketTimeoutException e) {
            throw new TimeoutException(e.getMessage(), e);
        } catch (HttpHostConnectException e) {
            throw new ConnectionLostException(e.getMessage(), e);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * リクエストを実行し、その結果をstreamに書き込む。
     * @param request リクエスト
     * @param ost 書き込み先ストリーム
     */
    private void executeWrite(HttpUriRequest request, FileOutputStream ost) {
        try (AutoCloseHttpClient client = createHttpClient()) {
            HttpResponse response = client.execute(request);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 400) {
                throw new HttpStatusException(status);
            }
            response.getEntity().writeTo(ost);
        } catch (SocketTimeoutException e) {
            throw new TimeoutException(e.getMessage(), e);
        } catch (HttpHostConnectException e) {
            throw new ConnectionLostException(e.getMessage(), e);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }
}