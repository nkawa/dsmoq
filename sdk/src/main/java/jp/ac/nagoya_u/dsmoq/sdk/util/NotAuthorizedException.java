package jp.ac.nagoya_u.dsmoq.sdk.util;

/**
 * サーバー側で認証に失敗した場合を表す例外
 */
public class NotAuthorizedException extends RuntimeException {
    public NotAuthorizedException() {
        super();
    }

    public NotAuthorizedException(String message) {
        super(message);
    }

    public NotAuthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotAuthorizedException(Throwable cause) {
        super(cause);
    }
}
