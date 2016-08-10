package jp.ac.nagoya_u.dsmoq.sdk.http;

import jp.ac.nagoya_u.dsmoq.sdk.util.ResourceNames;

import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.util.ResourceBundle;

public class AutoCloseHttpClient implements AutoCloseable {
    private static Marker LOG_MARKER = MarkerFactory.getMarker("SDK");
    private static Logger logger = LoggerFactory.getLogger(LOG_MARKER.toString());
    private static ResourceBundle resource = ResourceBundle.getBundle("message");

    private CloseableHttpClient client;

    /** HTTP Request のタイムアウト時間 (ms) */
    private static final int TIMEOUT = 30 * 1000;

    /** HTTP Request の Accept-Encoding ヘッダ */
    private static final String ACCEPT_ENCODING_HEADER_NAME = "Accept-Encoding";

    /** HTTP Response の Accept-Encoding ヘッダ に指定するgzip指定 */
    private static final String GZIP_ENCODING_NAME = "gzip";

    public AutoCloseHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();
        this.client = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultRequestConfig(requestConfig)
                .addInterceptorFirst(new HttpRequestInterceptor() {
                    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
                        if (!request.containsHeader(ACCEPT_ENCODING_HEADER_NAME)) {
                            request.addHeader(ACCEPT_ENCODING_HEADER_NAME, GZIP_ENCODING_NAME);
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
                                for (HeaderElement codec : codecs) {
                                    if (codec.getName().equalsIgnoreCase(GZIP_ENCODING_NAME)) {
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

    /** HTTP Response の Content-Disposition ヘッダ */
    private static final String RANGE_HEADER_NAME = "Range";

    public CloseableHttpResponse execute(HttpUriRequest request) throws IOException, HttpException {
        logger.debug(LOG_MARKER, resource.getString(ResourceNames.LOG_SEND_REQUEST), request);
        HttpContext context = new BasicHttpContext();
        CloseableHttpResponse response = this.client.execute(request, context);
        RedirectStrategy redirectStrategy = DefaultRedirectStrategy.INSTANCE;
        if (redirectStrategy.isRedirected(request, response, context)) {
            HttpUriRequest redirect = redirectStrategy.getRedirect(request, response, context);
            Header range = request.getFirstHeader(RANGE_HEADER_NAME);
            if (range != null) {
                redirect.setHeader(range);
            }
            logger.debug(LOG_MARKER, resource.getString(ResourceNames.LOG_REDIRECT), redirect);
            return this.client.execute(redirect, context);
        } else {
            return response;
        }
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
