package jp.ac.nagoya_u.dsmoq.sdk.http;

import org.apache.http.client.methods.HttpHead;
import java.net.URI;

/**
 * AutoCloseableに対応したHttpHeadのラッパー
 * @see <a href="http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/methods/HttpHead.html">HttpHead (Apache HttpClient 4.3.6 API)</a>
 */
public class AutoHttpHead extends HttpHead implements AutoCloseable {

    public AutoHttpHead() {

    }

    public AutoHttpHead(URI uri) {
        super(uri);
    }

    public AutoHttpHead(String uri) {
        super(uri);
    }

    @Override
    public void close() {
        abort();
    }
}
