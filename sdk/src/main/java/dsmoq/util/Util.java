package dsmoq.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;

final public class Util {
    public static String objectToJson(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        try (StringWriter sw = new StringWriter()) {
            mapper.writeValue(sw, obj);
            return sw.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
