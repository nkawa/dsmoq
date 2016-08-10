package jp.ac.nagoya_u.dsmoq.sdk.util;

/**
 * 不正なHTTPステータスの場合を表す例外
 */
public class HttpStatusException extends RuntimeException {

    public HttpStatusException() {
        super();
    }

    public HttpStatusException(int httpStatus) {
        super("http_status=" + httpStatus);
    }

    public HttpStatusException(int httpStatus, String body, Throwable cause) {
        super("http_status=" + httpStatus + ",body=" + body, cause);
    }

    public HttpStatusException(int httpStatus, Throwable cause) {
        super("http_status=" + httpStatus, cause);
    }

    public HttpStatusException(String message) {
        super(message);
    }

    public HttpStatusException(String message, Throwable cause) {
        super(message, cause);
    }

    public HttpStatusException(Throwable cause) {
        super(cause);
    }
}
