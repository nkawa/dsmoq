package dsmoq.sdk.client;

import dsmoq.sdk.http.AutoCloseHttpClient;
import dsmoq.sdk.http.AutoHttpGet;
import dsmoq.sdk.http.AutoHttpPost;
import dsmoq.sdk.http.AutoHttpPut;
import dsmoq.sdk.request.ChangePasswordParam;
import dsmoq.sdk.request.UpdateEmailParam;
import dsmoq.sdk.request.UpdateProfileParam;
import dsmoq.sdk.response.User;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by s.soyama on 2015/03/06.
 */
public class ProfileClient {

    private DsmoqClient client;

    public ProfileClient(DsmoqClient client) {
        this.client = client;
    }

    /**
     * ログインユーザのプロファイルを取得する。（GET /api/profile相当）
     * @return プロファイル
     */
    public User getProfile() {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + "/api/profile")){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toUser(json);
            }
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
        try (AutoHttpPut request = new AutoHttpPut((this.client.getBaseUrl() + "/api/profile"))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toUser(json);
            }
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
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + "/api/profile/image"))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addBinaryBody("icon", file);
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toUser(json);
            }
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
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + "/api/profile/email_change_requests"))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toUser(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ログインユーザのパスワードを変更する。（PUT /api/profile/password相当）
     * @param param パスワード変更情報
     */
    public void changePassword(ChangePasswordParam param) {
        try (AutoHttpPut request = new AutoHttpPut((this.client.getBaseUrl() + "/api/profile/password"))) {
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
}
