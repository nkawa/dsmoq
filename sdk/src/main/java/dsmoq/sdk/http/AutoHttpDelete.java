package dsmoq.sdk.http;

import org.apache.http.client.methods.HttpDelete;

import java.net.URI;

public class AutoHttpDelete extends HttpDelete implements AutoCloseable {

    public AutoHttpDelete() {
    }

    public AutoHttpDelete(URI uri) {
        super(uri);
    }

    public AutoHttpDelete(String uri) {
        super(uri);
    }

    @Override
    public void close() {
        abort();
    }
}