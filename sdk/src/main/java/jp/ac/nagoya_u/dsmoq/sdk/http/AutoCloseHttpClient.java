package jp.ac.nagoya_u.dsmoq.sdk.http;

import java.io.IOException;
import java.util.ResourceBundle;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import jp.ac.nagoya_u.dsmoq.sdk.util.ResourceNames;

public class AutoCloseHttpClient implements AutoCloseable {
    /** HTTP Request の Accept-Encoding ヘッダ */
    private static final String ACCEPT_ENCODING_HEADER_NAME = "Accept-Encoding";

    /** HTTP Response の Accept-Encoding ヘッダ に指定するgzip指定 */
    private static final String GZIP_ENCODING_NAME = "gzip";

    /** ログマーカー */
    private static final Marker LOG_MARKER = MarkerFactory.getMarker("SDK");

    /** ロガー */
    private static Logger logger = LoggerFactory.getLogger(LOG_MARKER.toString());

    /** HTTP Response の Content-Disposition ヘッダ */
    private static final String RANGE_HEADER_NAME = "Range";

    /** メッセージ用のリソースバンドル */
    private static ResourceBundle resource = ResourceBundle.getBundle("message");

    /** HTTP Request のタイムアウト時間 (ms) */
    private static final int TIMEOUT = 30 * 1000;

    private CloseableHttpClient client;

    public AutoCloseHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT)
                .build();
        this.client = HttpClientBuilder.create().disableRedirectHandling().setDefaultRequestConfig(requestConfig)
                .addInterceptorFirst(new HttpRequestInterceptor() {
                    public void process(final HttpRequest request, final HttpContext context)
                            throws HttpException, IOException {
                        if (!request.containsHeader(ACCEPT_ENCODING_HEADER_NAME)) {
                            request.addHeader(ACCEPT_ENCODING_HEADER_NAME, GZIP_ENCODING_NAME);
                        }
                    }
                }).addInterceptorFirst(new HttpResponseInterceptor() {
                    public void process(final HttpResponse response, final HttpContext context)
                            throws HttpException, IOException {
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
                }).build();
    }

    public void close() {
        try {
            this.client.close();
        } catch (IOException ioe) {
        } finally {
            this.client = null;
        }
    }

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
}
