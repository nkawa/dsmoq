package dsmoq.client;

import dsmoq.http.*;
import dsmoq.request.*;
import dsmoq.util.Util;
import org.apache.http.HttpEntity;
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

public class DsmoqClient implements AutoCloseable {
    private String _userName;
    private String _password;
    private String _baseUrl;

    private static final String SIGNIN = "/signin";
    private static final String SIGNOUT = "/signout";

    public DsmoqClient() {

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
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + SIGNIN)){
            HttpClient client = HttpClientBuilder.create().build();
            Signin signin = new Signin(_userName, _password);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("d", Util.objectToJson(signin)));
            request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            client.execute(request);
        } catch(IOException e) {
            System.out.println("error");
        }
    }

    /**
     *
     */
    @Override
    public void close() {
        try (AutoHttpPost request = new AutoHttpPost(_baseUrl + SIGNOUT)){
            HttpClient client = HttpClientBuilder.create().build();
            client.execute(request);
        } catch(IOException e) {

        }
    }
}
