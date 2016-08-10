package jp.ac.nagoya_u.dsmoq.sdk.http;

import org.apache.http.client.methods.HttpGet;

import java.net.URI;

/**
 * AutoCloseableに対応したHttpGetのラッパー
 * 
 * @see <a href=
 *      "http://hc.apache.org/httpcomponents-client-4.3.x/httpclient/apidocs/org/apache/http/client/methods/HttpGet.html">
 *      HttpGet (Apache HttpClient 4.3.6 API)</a>
 */
public class AutoHttpGet extends HttpGet implements AutoCloseable {

    /**
     * デフォルトコンストラクタ
     */
    public AutoHttpGet() {
        super();
    }

    /**
     * コンストラクタ
     * 
     * @param uri GETを呼び出す対象のURL
     */
    public AutoHttpGet(String uri) {
        super(uri);
    }

    /**
     * コンストラクタ
     * 
     * @param uri GETを呼び出す対象のURL
     */
    public AutoHttpGet(URI uri) {
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
