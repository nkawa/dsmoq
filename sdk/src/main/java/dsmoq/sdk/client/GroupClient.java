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
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupClient {

    private DsmoqClient client;

    public GroupClient(DsmoqClient client) {
        this.client = client;
    }
    
    /**
     * グループ一覧を取得する。（GET /api/groups相当）
     * @param param グループ一覧取得情報
     * @return グループ一覧情報
     */
    public RangeSlice<GroupsSummary> getGroups(GetGroupsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + "/api/groups?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toGroups(json);
            }
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
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/api/groups/%s", groupId))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toGroup(json);
            }
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
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/api/groups/%s/members?d=%s", groupId, URLEncoder.encode(param.toJsonString(), "UTF-8")))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toMembers(json);
            }
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
        try (AutoHttpPost request = new AutoHttpPost(this.client.getBaseUrl() + "/api/groups")) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toGroup(json);
            }
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
        try (AutoHttpPut request = new AutoHttpPut(this.client.getBaseUrl() + String.format("/api/groups/%s", groupId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toGroup(json);
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループの画像一覧を取得する。
     * @param groupId グループID
     * @param param 一覧取得情報
     * @return グループの画像一覧情報
     */
    public RangeSlice<GroupGetImage> getGroupImage(String groupId, GetRangeParam param) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/api/groups/%s/images?d=", groupId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toGroupGetImage(json);
            }
        } catch(IOException e) {
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
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + String.format("/api/groups/%s/images", groupId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toGroupAddImages(json);
            }
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
        try (AutoHttpPut request = new AutoHttpPut(this.client.getBaseUrl() + String.format("/api/groups/%s/images/primary", groupId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
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
        try (AutoHttpDelete request = new AutoHttpDelete(this.client.getBaseUrl() + String.format("/api/groups/%s/images/%s", groupId, imageId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toGroupDeleteImage(json);
            }
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
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + String.format("/api/groups/%s/members", groupId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", AddMemberParam.toJsonString(param)));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
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
        try (AutoHttpPut request = new AutoHttpPut((this.client.getBaseUrl() + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
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
        try (AutoHttpDelete request = new AutoHttpDelete((this.client.getBaseUrl() + String.format("/api/groups/%s/members/%s", groupId, userId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * グループを削除する。（DELETE /api/groups/${group_id}相当）
     * @param groupId グループID
     */
    public void deleteGroup(String groupId) {
        try (AutoHttpDelete request = new AutoHttpDelete((this.client.getBaseUrl() + String.format("/api/groups/%s", groupId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

}
