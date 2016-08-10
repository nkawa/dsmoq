package jp.ac.nagoya_u.dsmoq.sdk.http;

import org.apache.http.client.methods.HttpPost;
import java.net.URI;

/**
 * AutoCloseableに対応したHttpPostのラッパー
 * 
 * @see <a href=
 *      "http://hc.apache.org/httpcomponents-client-4.3.x/httpclient/apidocs/org/apache/http/client/methods/HttpPost.html">
 *      HttpPost (Apache HttpClient 4.3.6 API)</a>
 */
public class AutoHttpPost extends HttpPost implements AutoCloseable {

    /**
     * デフォルトコンストラクタ
     */
    public AutoHttpPost() {
        super();
    }

    /**
     * コンストラクタ
     * 
     * @param uri POSTを呼び出す対象のURL
     */
    public AutoHttpPost(String uri) {
        super(uri);
    }

    /**
     * コンストラクタ
     * 
     * @param uri POSTを呼び出す対象のURL
     */
    public AutoHttpPost(URI uri) {
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
