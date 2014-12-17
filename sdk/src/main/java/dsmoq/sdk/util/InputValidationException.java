package dsmoq.sdk.util;

import java.util.ArrayList;
import java.util.List;

public class InputValidationException extends RuntimeException {
    private List<InputValidationError> errors;
    public InputValidationException(List<InputValidationError> errors) {
        this.errors = new ArrayList<>(errors);
    }

    public List<InputValidationError> getErrorMessages() {
        return new ArrayList<>(this.errors);
    }
}
