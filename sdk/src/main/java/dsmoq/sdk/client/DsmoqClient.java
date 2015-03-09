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

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

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
     * ユーザー一覧を取得する。（GET /api/accounts相当）
     * @return ユーザー一覧
     */
    public List<User> getAccounts() {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/accounts")){
            try(AutoCloseHttpClient client = createHttpClient()) {
                addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toUsers(json);
            }
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
            try(AutoCloseHttpClient client = createHttpClient()) {
                addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toLicenses(json);
            }
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
            try(AutoCloseHttpClient client = createHttpClient()) {
                addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toTaskStatus(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * 統計情報を取得します。
     * @param param 統計情報期間指定
     * @return 統計情報
     */
    public List<StatisticsDetail> getStatistics(StatisticsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/statistics?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            try(AutoCloseHttpClient client = createHttpClient()) {
                addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toStatistics(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * BaseUrlを返却する。
     * @return BaseUrl
     */
    String getBaseUrl() {
        return _baseUrl;
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
    AutoCloseHttpClient createHttpClient() {
        return new AutoCloseHttpClient();
    }
}
