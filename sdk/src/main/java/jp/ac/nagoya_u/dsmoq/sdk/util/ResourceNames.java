package jp.ac.nagoya_u.dsmoq.sdk.util;

/**
 * リソースバンドルのキー項目名の列挙
 */
public class ResourceNames {
    /**
     * エラーが発生した場合のログに用いるリソース名
     */
    public static String LOG_ERROR_OCCURED = "log_error_occured";

    /**
     * null検査に失敗した場合のログに用いるリソース名
     */
    public static String LOG_INVALID_NULL = "log_invalid_null";

    /**
     * null検査に失敗した場合の例外メッセージに用いるリソース名
     */
    public static String ERR_INVALID_NULL = "err_invalid_null";

    /**
     * 非空検査に失敗した場合のログに用いるリソース名
     */
    public static String LOG_INVALID_EMPTY = "log_invalid_empty";

    /**
     * 非空検査に失敗した場合の例外メッセージに用いるリソース名
     */
    public static String ERR_INVALID_EMPTY = "err_invalid_empty";

    /**
     * 基準値以上であるかの検査に失敗した場合のログに用いるリソース名
     */
    public static String LOG_INVALID_NOT_GREATER_OR_EQUAL = "log_invalid_not_greater_or_equal";

    /**
     * 基準値以上であるかの検査に失敗した場合の例外メッセージに用いるリソース名
     */
    public static String ERR_INVALID_NOT_GREATER_OR_EQUAL = "err_invalid_not_greater_or_equal";

    /**
     * HTTPレスポンスのContent-Lengthが見つからなかった場合のログに用いるリソース名
     */
    public static String LOG_CONTENT_LENGTH_NOT_FOUND = "log_content_length_not_found";

    /**
     * HTTPレスポンスのContent-Lengthが不正な場合のログに用いるリソース名
     */
    public static String LOG_INVALID_CONTENT_LENGTH = "log_invalid_content_length";

    /**
     * HTTPレスポンスのContent-Dispositionが見つからなかった場合のログに用いるリソース名
     */
    public static String LOG_CONTENT_DISPOSITION_NOT_FOUND = "log_content_disposition_not_found";

    /**
     * HTTPレスポンスのContent-Dispositionの形式が不正な場合のログに用いるリソース名
     */
    public static String LOG_ILLEGAL_FORMAT_CONTENT_DISPOSITION = "log_illegal_format_content_disposition";

    /**
     * 文字コードをサポートしていない場合のログに用いるリソース名
     */
    public static String LOG_UNSUPPORTED_CHARSET = "log_unsupported_charset";

    /**
     * HTTPリクエスト送信のログに用いるリソース名
     */
    public static String LOG_SEND_REQUEST = "log_send_request";

    /**
     * リダイレクト処理のログに用いるリソース名
     */
    public static String LOG_REDIRECT = "log_redirect";
}
