package dsmoq.sdk.util;

public class ApiFailedException extends RuntimeException {
    public ApiFailedException() {
        super();
    }
    public ApiFailedException(String message) {
        super(message);
    }

    public ApiFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApiFailedException(Throwable cause) {
        super(cause);
    }
}
