package dsmoq.models;

/**
 * APIのステータス文字列の定義体
 */
@:enum abstract ApiStatus(String) to String {
    var OK = "OK";
    var NotFound = "NotFound";
    var BadRequest = "BadRequest";
    var Unauthorized = "Unauthorized";
    var Error = "Error";
    var IllegalArgument = "Illegal Argument";
    var AccessDenied = "AccessDenied";
}
