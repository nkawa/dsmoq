package jp.ac.nagoya_u.dsmoq.sdk.http;

import org.apache.http.client.methods.HttpGet;

import java.net.URI;

public class AutoHttpGet extends HttpGet implements AutoCloseable {

    public AutoHttpGet() {
    }

    public AutoHttpGet(URI uri) {
        super(uri);
    }

    public AutoHttpGet(String uri) {
        super(uri);
    }

    @Override
    public void close() {
        abort();
    }
}
