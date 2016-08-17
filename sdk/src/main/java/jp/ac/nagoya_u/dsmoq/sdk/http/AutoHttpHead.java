package jp.ac.nagoya_u.dsmoq.sdk.http;

import java.net.URI;

import org.apache.http.client.methods.HttpHead;

/**
 * AutoCloseableに対応したHttpHeadのラッパー
 * 
 * @see <a href=
 *      "http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/methods/HttpHead.html">
 *      HttpHead (Apache HttpClient 4.3.6 API)</a>
 */
public class AutoHttpHead extends HttpHead implements AutoCloseable {

    /**
     * デフォルトコンストラクタ
     */
    public AutoHttpHead() {
        super();
    }

    /**
     * コンストラクタ
     * 
     * @param uri HEADを呼び出す対象のURL
     */
    public AutoHttpHead(String uri) {
        super(uri);
    }

    /**
     * コンストラクタ
     * 
     * @param uri HEADを呼び出す対象のURL
     */
    public AutoHttpHead(URI uri) {
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
