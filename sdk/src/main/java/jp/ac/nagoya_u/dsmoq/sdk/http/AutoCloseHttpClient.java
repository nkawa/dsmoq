package jp.ac.nagoya_u.dsmoq.sdk.http;

import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

public class AutoCloseHttpClient implements AutoCloseable {
    private CloseableHttpClient client;

    public AutoCloseHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(30 * 1000).setSocketTimeout(30 * 1000).build();
        this.client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .addInterceptorFirst(new HttpRequestInterceptor() {
                    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
                        if (!request.containsHeader("Accept-Encoding")) {
                            request.addHeader("Accept-Encoding", "gzip");
                        }
                    }
                })
                .addInterceptorFirst(new HttpResponseInterceptor() {
                    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            Header ceheader = entity.getContentEncoding();
                            if (ceheader != null) {
                                HeaderElement[] codecs = ceheader.getElements();
                                for (int i = 0; i < codecs.length; i++) {
                                    if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                                        response.setEntity(new GzipDecompressingEntity(response.getEntity()));
                                        return;
                                    }
                                }
                            }
                        }
                    }
                })
                .build();

    }

    public CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
        return this.client.execute(request);
    }

    public void close() {
        try {
            this.client.close();
        } catch (IOException ioe) {
        } finally {
            this.client = null;
        }
    }
}
