package dsmoq.sdk.client;

import dsmoq.sdk.http.*;
import dsmoq.sdk.request.*;
import dsmoq.sdk.request.json.ChangeStorageJson;
import dsmoq.sdk.response.*;
import dsmoq.sdk.util.ApiFailedException;
import dsmoq.sdk.util.JsonUtil;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import org.apache.http.entity.mime.MultipartEntityBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
        DsmoqClient client = new DsmoqClient(baseUrl, userName, password);
        client.signin();
        return client;
    }

    /**
     * クライアントオブジェクトを生成する。
     * @param baseUrl 基準となるURL
     * @param userName ユーザーアカウント
     * @param password パスワード
     */
    public DsmoqClient(String baseUrl, String userName, String password) {
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
     *
     * @param datasetId
     * @param fileId
     */
    public void deleteFile(String datasetId, String fileId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param datasetId
     * @param param
     */
    public void updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/metadata", datasetId))) {
            HttpClient client = getClient();
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
     *
     * @param datasetId
     * @param files
     * @return
     */
    public DatasetAddImages addImagesToDataset(String datasetId, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/images", datasetId)))) {
            HttpClient client = getClient();
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
     *
     * @param datasetId
     * @param param
     */
    public void setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/images/primary", datasetId))) {
            HttpClient client = getClient();
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
     *
     * @param datasetId
     * @param imageId
     * @return
     */
    public DatasetDeleteImage deleteImageToDataset(String datasetId, String imageId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s/images/%s", datasetId, imageId))) {
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDatasetDeleteImage(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param datasetId
     * @param params
     * @return
     */
    public DatasetOwnerships changeAccessLevel(String datasetId, List<SetAccessLevelParam> params) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/acl", datasetId)))) {
            HttpClient client = getClient();
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
     *
     * @param datasetId
     * @param param
     */
    public void changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/guest_access", datasetId))) {
            HttpClient client = getClient();
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
     *
     * @param datasetId
     */
    public void deleteDataset(String datasetId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/datasets/%s", datasetId))) {
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param datasetId
     * @param param
     * @return
     */
    public DatasetTask changeDatasetStorage(String datasetId, ChangeStorageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/datasets/%s/storage", datasetId))) {
            HttpClient client = getClient();
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
     *
     * @param param
     * @return
     */
    public RangeSlice<GroupsSummary> getGroups(GetGroupsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/groups?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroups(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param groupId
     * @return
     */
    public Group getGroup(String groupId) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s", groupId))){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroup(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param groupId
     * @param param
     * @return
     */
    public RangeSlice<MemberSummary> getMembers(String groupId, GetMembersParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/groups/%s/members?d=%s", groupId, URLEncoder.encode(param.toJsonString(), "UTF-8")))){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toMembers(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param param
     * @return
     */
    public Group createGroup(CreateGroupParam param) {
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + "/api/groups")) {
            HttpClient client = getClient();
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
     *
     * @param groupId
     * @param param
     * @return
     */
    public Group updateGroup(String groupId, UpdateGroupParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/groups/%s", groupId))) {
            HttpClient client = getClient();
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
     *
     * @param groupId
     * @param files
     * @return
     */
    public GroupAddImages addImagesToGroup(String groupId, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/images", groupId)))) {
            HttpClient client = getClient();
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
     *
     * @param groupId
     * @param param
     */
    public void setPrimaryImageToGroup(String groupId, SetPrimaryImageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(_baseUrl + String.format("/api/groups/%s/images/primary", groupId))) {
            HttpClient client = getClient();
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
     *
     * @param groupId
     * @param imageId
     * @return
     */
    public GroupDeleteImage deleteImageToGroup(String groupId, String imageId) {
        try (AutoHttpDelete request = new AutoHttpDelete(_baseUrl + String.format("/api/groups/%s/images/%s", groupId, imageId))) {
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toGroupDeleteImage(json);
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param groupId
     * @param param
     */
    public void addMember(String groupId, List<AddMemberParam> param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/members", groupId)))) {
            HttpClient client = getClient();
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
     *
     * @param groupId
     * @param userId
     * @param param
     */
    public void setMemberRole(String groupId, String userId, SetMemberRoleParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            HttpClient client = getClient();
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
     *
     * @param groupId
     * @param userId
     */
    public void deleteMember(String groupId, String userId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param groupId
     */
    public void deleteGroup(String groupId) {
        try (AutoHttpDelete request = new AutoHttpDelete((_baseUrl + String.format("/api/groups/%s", groupId)))) {
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            JsonUtil.statusCheck(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @return
     */
    public List<License> getLicenses() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/licenses")){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toLicenses(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @return
     */
    public User getProfile() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/profile")){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toUser(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param param
     * @return
     */
    public User updateProfile(UpdateProfileParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile"))) {
            HttpClient client = getClient();
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
     *
     * @param file
     * @return
     */
    public User updateProfileIcon(File file) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/image"))) {
            HttpClient client = getClient();
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
     *
     * @param param
     * @return
     */
    public User updateEmail(UpdateEmailParam param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/profile/email_change_requests"))) {
            HttpClient client = getClient();
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
     *
     * @param param
     */
    public void changePassword(ChangePasswordParam param) {
        try (AutoHttpPut request = new AutoHttpPut((_baseUrl + "/api/profile/password"))) {
            HttpClient client = getClient();
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
     *
     * @return
     */
    public List<User> getAccounts() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/accounts")){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toUsers(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param datasetId
     * @param fileId
     * @param downloadDirectory
     * @return
     */
    public File downloadFile(String datasetId, String fileId, String downloadDirectory) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/files/%s/%s", datasetId, fileId))){
            HttpClient client = getClient();
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
     *
     * @param taskId
     * @return
     */
    public TaskStatus getTaskStatus(String taskId) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + String.format("/api/tasks/%s", taskId))){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toTaskStatus(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
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
     *
     * @return
     */
    private HttpClient getClient() {
        if (client == null) {
            client = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build();
        }
        return client;
    }
}
