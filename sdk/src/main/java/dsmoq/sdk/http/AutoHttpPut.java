package dsmoq.sdk.http;

import org.apache.http.client.methods.HttpPut;
import java.net.URI;

public class AutoHttpPut extends HttpPut implements AutoCloseable {

    public AutoHttpPut() {

    }

    public AutoHttpPut(URI uri) {
        super(uri);
    }

    public AutoHttpPut(String uri) {
        super(uri);
    }

    @Override
    public void close() {
        abort();
    }
}