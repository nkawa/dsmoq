package jp.ac.nagoya_u.dsmoq.sdk.util;

/**
 * DsmoqClientで送出する総合的な例外クラス
 */
public class DsmoqHttpException extends RuntimeException {

    public DsmoqHttpException() {
        super();
    }

    public DsmoqHttpException(String message) {
        super(message);
    }

    public DsmoqHttpException(String message, Throwable cause) {
        super(message, cause);
    }

    public DsmoqHttpException(Throwable cause) {
        super(cause);
    }
}
