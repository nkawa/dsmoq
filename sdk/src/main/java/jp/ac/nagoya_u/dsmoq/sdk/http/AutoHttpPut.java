package jp.ac.nagoya_u.dsmoq.sdk.http;

import org.apache.http.client.methods.HttpPut;
import java.net.URI;

/**
 * AutoCloseableに対応したHttpPutのラッパー
 * 
 * @see <a href=
 *      "http://hc.apache.org/httpcomponents-client-4.3.x/httpclient/apidocs/org/apache/http/client/methods/HttpPut.html">
 *      HttpPut (Apache HttpClient 4.3.6 API)</a>
 */
public class AutoHttpPut extends HttpPut implements AutoCloseable {

    /**
     * デフォルトコンストラクタ
     */
    public AutoHttpPut() {
        super();
    }

    /**
     * コンストラクタ
     * 
     * @param uri PUTを呼び出す対象のURL
     */
    public AutoHttpPut(String uri) {
        super(uri);
    }

    /**
     * コンストラクタ
     * 
     * @param uri PUTを呼び出す対象のURL
     */
    public AutoHttpPut(URI uri) {
        super(uri);
    }

    /**
     * リクエストの実行をAbortします。
     */
    @Override
    public void close() {
        abort();
    }
}
