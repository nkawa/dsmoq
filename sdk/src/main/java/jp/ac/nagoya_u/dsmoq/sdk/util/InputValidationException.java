package jp.ac.nagoya_u.dsmoq.sdk.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 入力値が不正であることを表す例外
 */
public class InputValidationException extends RuntimeException {
    private List<InputValidationError> errors;
    public InputValidationException(List<InputValidationError> errors) {
        this.errors = new ArrayList<>(errors);
    }

    public List<InputValidationError> getErrorMessages() {
        return new ArrayList<>(this.errors);
    }
}
