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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import org.apache.http.entity.mime.MultipartEntityBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 *
 */
public class DsmoqClient implements AutoCloseable {
    private String _userName;
    private String _password;
    private String _baseUrl;
    private boolean isSignin;
    private HttpClient client;

    /**
     *
     * @param baseUrl
     * @param userName
     * @param password
     * @return
     */
    public static DsmoqClient signin(String baseUrl, String userName, String password) {
        DsmoqClient client = new DsmoqClient(baseUrl, userName, password);
        client.signin();
        return client;
    }

    /**
     *
     * @param baseUrl
     * @param userName
     * @param password
     */
    public DsmoqClient(String baseUrl, String userName, String password) {
        this._baseUrl = baseUrl;
        this._userName = userName;
        this._password = password;
    }

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
     *
     * @param param
     * @return
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
     *
     * @param datasetId
     * @return
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
     *
     * @param files
     * @return
     */
    public Dataset createDataset(File... files) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + "/api/datasets"))) {
            HttpClient client = getClient();
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("files", file));
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDataset(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param datasetId
     * @param files
     * @return
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
     *
     * @param datasetId
     * @param fileId
     * @param file
     * @return
     */
    public DatasetFile updateFile(String datasetId, String fileId, File file) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            HttpClient client = getClient();
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file);
            request.setEntity(builder.build());
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDataseetFile(json);
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     *
     * @param datasetId
     * @param fileId
     * @param param
     * @return
     */
    public DatasetFile updateFileMetaInfo(String datasetId, String fileId, UpdateFileMetaParam param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/datasets/%s/files/%s/metadata", datasetId, fileId)))) {
            HttpClient client = getClient();
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", param.toJsonString()));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDataseetFile(json);
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
    public DatasetTask changeDatasetStorage(String datasetId, ChangeStorageJson param) {
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
    public void addMember(String groupId, AddMemberParam param) {
        try (AutoHttpPost request = new AutoHttpPost((_baseUrl + String.format("/api/groups/%s/members", groupId)))) {
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
     */
    @Override
    public void close() {
        signout();
    }

    /**
     *
     * @return
     */
    private HttpClient getClient() {
        if (client == null) {
            client = HttpClientBuilder.create().build();
        }
        return client;
    }
}
