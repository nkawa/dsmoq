package dsmoq.sdk.util;

/**
 * サーバー側とのコネクションが失われた・接続に失敗したことを表す例外
 */
public class ConnectionLostException extends RuntimeException {
    public ConnectionLostException() {
        super();
    }

    public ConnectionLostException(String message) {
        super(message);
    }

    public ConnectionLostException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionLostException(Throwable cause) {
        super(cause);
    }
}
