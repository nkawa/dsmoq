package dsmoq.sdk.client;

import dsmoq.sdk.http.*;
import dsmoq.sdk.request.json.ChangeStorageJson;
import dsmoq.sdk.request.GetDatasetParam;
import dsmoq.sdk.request.SigninParam;
import dsmoq.sdk.response.DatasetTask;
import dsmoq.sdk.response.DatasetsSummary;
import dsmoq.sdk.response.RangeSlice;
import dsmoq.sdk.util.JsonUtil;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

public class DsmoqClient implements AutoCloseable {
    private String _userName;
    private String _password;
    private String _baseUrl;
    private boolean isSignin;
    private HttpClient client;

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
            client.execute(request);
        } catch(IOException e) {
            System.out.println("error");
        }
        isSignin = true;
    }

    public void signout() {
        if (!isSignin) {
            return;
        }
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + "/api/signout")){
            HttpClient client = getClient();
            client.execute(request);
        } catch(IOException e) {

        }
    }

    public RangeSlice<DatasetsSummary> getDataset(GetDatasetParam param) {
        try (AutoHttpGet request = new AutoHttpGet(_baseUrl + "/api/datasets?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            HttpClient client = getClient();
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity());
            return JsonUtil.toDataset(json);
        } catch(IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

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
            System.out.println(e.getMessage());
            return null;
        }
    }

    /**
     *
     */
    @Override
    public void close() {
        signout();
    }

    private HttpClient getClient() {
        if (client == null) {
            client = HttpClientBuilder.create().build();
        }
        return client;
    }
}
