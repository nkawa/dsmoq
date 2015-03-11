package dsmoq.sdk.util;

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

    public HttpStatusException(String message){
        super(message);
    }

    public HttpStatusException(String message, Throwable cause) {
        super(message, cause);
    }

    public HttpStatusException(Throwable cause) {
        super(cause);
    }
}
