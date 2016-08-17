
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import jp.ac.nagoya_u.dsmoq.sdk.util.HttpStatusException;

public class HttpStatusExceptionMatcher extends TypeSafeMatcher<HttpStatusException> {
    private final int expectedStatusCode;
    private final String expectedStatusMessage;
    
    private HttpStatusExceptionMatcher(int expectedStatusCode, String expectedStatusMessage) {
        this.expectedStatusCode = expectedStatusCode;
        this.expectedStatusMessage = expectedStatusMessage;
    }

    public static HttpStatusExceptionMatcher is(int expectedStatusCode) {
        return new HttpStatusExceptionMatcher(expectedStatusCode, null);
    }

    public static HttpStatusExceptionMatcher is(int expectedStatusCode, String expectedStatusMessage) {
        return new HttpStatusExceptionMatcher(expectedStatusCode, expectedStatusMessage);
    }

    @Override
    protected boolean matchesSafely(HttpStatusException e) {
        return e.getMessage().startsWith("http_status=" + expectedStatusCode)
            && (expectedStatusMessage == null || e.getMessage().contains("\"status\":\"" + expectedStatusMessage + "\""))
        ;
    }

    @Override
    public void describeTo(Description description) {
        if (expectedStatusMessage == null) {
            description.appendValue("http_status=" + expectedStatusCode);
        } else {
            description.appendValue("http_status=" + expectedStatusCode + ",body={\"status\":\"" + expectedStatusMessage + "\"}");
        }
    }

    @Override
    protected void describeMismatchSafely(HttpStatusException e, Description description) {
        description.appendValue(e.getMessage());
    }
}
