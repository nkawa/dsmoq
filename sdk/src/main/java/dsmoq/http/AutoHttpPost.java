package dsmoq.http;

import org.apache.http.client.methods.HttpPost;
import java.net.URI;

public class AutoHttpPost extends HttpPost implements AutoCloseable {

    public AutoHttpPost() {

    }

    public AutoHttpPost(URI uri) {
        super(uri);
    }

    public AutoHttpPost(String uri) {
        super(uri);
    }

    @Override
    public void close() {
        abort();
    }
}
