package dsmoq.sdk.http;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;

import java.io.IOException;

/**
 * Created by s.soyama on 2015/03/09.
 */
public class AutoCloseHttpClient implements AutoCloseable {
    private CloseableHttpClient client;

    public AutoCloseHttpClient() {
        this.client = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build();
    }

    public CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
        return this.client.execute(request);
    }

    public void close() {
        try {
            this.client.close();
        } catch (IOException ioe) {
        } finally {
            this.client = null;
        }
    }
}
