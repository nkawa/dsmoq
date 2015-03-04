package dsmoq.sdk.client;

import dsmoq.sdk.http.*;
import dsmoq.sdk.request.*;
import dsmoq.sdk.response.*;
import dsmoq.sdk.util.ApiFailedException;
import dsmoq.sdk.util.JsonUtil;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import org.apache.http.entity.mime.MultipartEntityBuilder;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * dsmoq APIを叩くためのクライアントクラス
 * 個々のAPIとの対比はJavaDocとAPIのドキュメントを比較してみてください。
 */
public class DsmoqClient implements AutoCloseable {
    private String _userName;
    private String _password;
    private String _baseUrl;
    private String _apiKey;
    private String _secretKey;
    private boolean isSignin;
    private CloseableHttpClient client;

    /**
     * クライアントオブジェクトを生成し、同時にサインインを行う。
     * (/api/signin相当)
     * @param baseUrl 基準となるURL
     * @param userName ユーザーアカウント
     * @param password パスワード
     * @return 作成したクライアント
     */
    public static DsmoqClient signin(String baseUrl, String userName, String password) {
        DsmoqClient client = new DsmoqClient(baseUrl, "", "", userName, password);
        client.signin();
        return client;
    }

    /**
     * APIキー、シークレットキーを使用するクライアントオブジェクトを生成する。
     * @param baseUrl 基準となるURL
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     * @return 作成したクライアント
     */
    public static DsmoqClient create(String baseUrl, String apiKey, String secretKey) {
        return new DsmoqClient(baseUrl, apiKey, secretKey, "", "");
    }

    /**
     * クライアントオブジェクトを生成する。
     * @param baseUrl 基準となるURL
     * @param apiKey APIキー
     * @param secretKey シークレットキー
     * @param userName ユーザーアカウント
     * @param password パスワード
     */
    public DsmoqClient(String baseUrl, String apiKey, String secretKey, String userName, String password) {
        this._apiKey = apiKey;
        this._secretKey = secretKey;
        this._baseUrl = baseUrl;
        this._userName = userName;
        this._password = password;
    }

    /**
     * サインインを行う。(POST /api/signin相当)
     */
    public void signin() {
        if (isSignin) {
            return;
        }
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + "/api/signin")){
            HttpClient client = getClient();
            SigninParam signin = new SigninParam(_userName, _password);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", signin.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
        isSignin = true;
    }

    /**
     * サインアウトを行う。(POST /api/signout相当)
     */
    public void signout() {
        if (!isSignin) {
            return;
        }
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + "/api/signout")){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * Datasetを検索する。(GET /api/datasets相当)
     * @param param Dataset検索に使用するパラメタ
     * @return 検索結果
     */
    public RangeSlice<DatasetsSummary> getDatasets(GetDatasetsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/datasets?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasets(json);
        } catch(IOException e) {
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDataset(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDataset
     */
    public Dataset createDataset(File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("file[]", file));
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDataset(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("files", file));
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetAddFiles(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetFile(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetFile(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットからファイルを削除する。（DELETE /api/datasets/${dataset_id}/files/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     */
    public void deleteFile(String datasetId, String fileId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットの情報を更新する。(PUT /api/datasets.${dataset_id}/metadata相当)
     * @param datasetId DatasetID
     * @param param データセット更新情報
     */
    public void updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/metadata", datasetId))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetAddImages(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。（PUT /api/datasets/${dataset_id}/image/primary相当）
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     */
    public void setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/images/primary", datasetId))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットから画像を削除する。（DELETE /api/datasets/${dataset_id}/image/${image_id}相当）
     * @param datasetId DatasetID
     * @param imageId 画像ID
     * @return 画像削除後のデータセットのメイン画像情報
     */
    public DatasetDeleteImage deleteImageToDataset(String datasetId, String imageId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s/images/%s", datasetId, imageId))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetDeleteImage(json);
        } catch (IOException e) {
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> p = new ArrayList<>();
            p.add(new BasicNameValuePair("d", SetAccessLevelParam.toJsonString(params)));
            request.setEntity(new UrlEncodedFormEntity(p, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetOwnerships(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。（PUT /api/datasets/${dataset_id}/guest_access相当）
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     */
    public void changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/guest_access", datasetId))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットを削除する。(DELETE /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     */
    public void deleteDataset(String datasetId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s", datasetId))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetTask(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループ一覧を取得する。（GET /api/groups相当）
     * @param param グループ一覧取得情報
     * @return グループ一覧情報
     */
    public RangeSlice<GroupsSummary> getGroups(GetGroupsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/groups?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroups(json);
        } catch(IOException e) {
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroup(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toMembers(json);
        } catch(IOException e) {
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroup(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroup(json);
        } catch (IOException e) {
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroupAddImages(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループに一覧で表示するメイン画像を設定する。（PUT /api/groups/${group_id}/images/primary相当）
     * @param groupId グループID
     * @param param メイン画像指定情報
     */
    public void setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/groups/%s/images/primary", groupId))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループから画像を削除する。（DELETE /api/groups/${group_id}/images/${image_id}相当）
     * @param groupId グループID
     * @param imageId 画像ID
     * @return 画像削除後のグループのメイン画像情報
     */
    public GroupDeleteImage deleteImageToGroup(String groupId, String imageId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/groups/%s/images/%s", groupId, imageId))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroupDeleteImage(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループにメンバーを追加する。（POST /api/groups/${group_id}/members相当）
     * @param groupId グループID
     * @param param メンバー追加情報
     */
    public void addMember(String groupId, List<AddMemberParam> param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/members", groupId)))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", AddMemberParam.toJsonString(param)));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * メンバーを削除する。（DELETE /api/groups/${group_id}/members/${user_id}相当）
     * @param groupId グループID
     * @param userId ユーザーID
     */
    public void deleteMember(String groupId, String userId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループを削除する。（DELETE /api/groups/${group_id}相当）
     * @param groupId グループID
     */
    public void deleteGroup(String groupId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s", groupId)))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ライセンス一覧を取得する。（GET /api/licenses相当）
     * @return ライセンス一覧情報
     */
    public List<License> getLicenses() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/licenses")){
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toLicenses(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ログインユーザのプロファイルを取得する。（GET /api/profile相当）
     * @return プロファイル
     */
    public User getProfile() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/profile")){
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toUser(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ログインユーザのプロファイルを更新する。（PUT /api/profile相当）
     * @param param プロファイル更新情報
     * @return プロファイル
     */
    public User updateProfile(UpdateProfileParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile"))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toUser(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ログインユーザの画像を更新する。（POST /api/profile/image相当）
     * @param file 画像ファイル
     * @return プロファイル
     */
    public User updateProfileIcon(File file) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/image"))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("icon", file);
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toUser(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ログインユーザのE-Mailを変更する。（POST /api/profile/email_change_request相当）
     * @param param E-Mail変更情報
     * @return プロファイル
     */
    public User updateEmail(UpdateEmailParam param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/email_change_requests"))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toUser(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ログインユーザのパスワードを変更する。（PUT /api/profile/password相当）
     * @param param パスワード変更情報
     */
    public void changePassword(ChangePasswordParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile/password"))) {
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ユーザー一覧を取得する。（GET /api/accounts相当）
     * @return ユーザー一覧
     */
    public List<User> getAccounts() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/accounts")){
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toUsers(json);
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
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);

            Dataset dataset = getDataset(datasetId);
            DatasetFile targetFile = dataset.getFiles().stream().filter(x -> x.getId().equals(fileId)).findFirst().get();

            File file = Paths.get(downloadDirectory, targetFile.getName()).toFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                response.getEntity().writeTo(fos);
            }
            return file;
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * タスクの現在のステータスを取得する。（GET /api/tasks/${task_id}相当）
     * @param taskId タスクID
     * @return タスクのステータス情報
     */
    public TaskStatus getTaskStatus(String taskId) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/tasks/%s", taskId))){
            HttpClient client = getClient();
            addAuthorizationHeader(request);
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toTaskStatus(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * クライアントを終了する。
     */
    @Override
    public void close() {
        try {
            signout();
        } catch (Exception e) {
            // do nothing
        }
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            // do nothing
        }
        client = null;
    }

    /**
     * Authorizationヘッダを追加する。
     * @param request リクエストオブジェクト
     */
    private void addAuthorizationHeader(HttpRequestBase request) {
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
    private HttpClient getClient() {
        if (client == null) {
            client = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build();
        }
        return client;
    }
}
