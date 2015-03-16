package jp.ac.nagoya_u.dsmoq.sdk.util;

/**
 * 不正な入力値を名前とメッセージで管理するためのクラス
 */
public class InputValidationError {
    private String name;
    private String message;

    public InputValidationError() {

    }

    public InputValidationError(String name, String message) {
        this.name = name;
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
