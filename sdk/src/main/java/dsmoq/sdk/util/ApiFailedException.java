package dsmoq.sdk.util;

/**
 * 何らかの理由でAPIの実行に失敗したことを表す例外
 */
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
