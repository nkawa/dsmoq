package jp.ac.nagoya_u.dsmoq.sdk.http;

import org.apache.http.client.methods.HttpDelete;

import java.net.URI;

/**
 * AutoCloseableに対応したHttpDeleteのラッパー
 * @see <a href="http://hc.apache.org/httpcomponents-client-4.3.x/httpclient/apidocs/org/apache/http/client/methods/HttpDelete.html">HttpDelete (Apache HttpClient 4.3.6 API)</a>
 */
public class AutoHttpDelete extends HttpDelete implements AutoCloseable {

    /**
     * デフォルトコンストラクタ
     */
    public AutoHttpDelete() {
        super();
    }

    /**
     * コンストラクタ
     * @param uri DELETEを呼び出す対象のURL
     */
    public AutoHttpDelete(URI uri) {
        super(uri);
    }

    /**
     * コンストラクタ
     * @param uri DELETEを呼び出す対象のURL
     */
    public AutoHttpDelete(String uri) {
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
