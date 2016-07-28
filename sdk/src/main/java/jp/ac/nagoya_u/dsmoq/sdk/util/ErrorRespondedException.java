package jp.ac.nagoya_u.dsmoq.sdk.util;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.util.EntityUtils;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.io.IOException;

/**
 * サーバ側からエラーレスポンスが返ってきたことを表す例外
 */
public class ErrorRespondedException extends Exception {
    /** HTTP Response Status Code */
    private int statusCode;
    /** HTTP Response Reason Phrase */
    private String reasonPhrase;
    /** HTTP Response Header */
    private String header;
    /** HTTP Response Body */
    private String body;
    /**
     * 指定されたHttpResponseを用いて、この例外を構築します。
     * @param response 元となるHttpResponse
     */
    public ErrorRespondedException(HttpResponse response) throws IOException {
        this(
            response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase(),
            Arrays.stream(response.getAllHeaders()).map(Header::toString).collect(Collectors.joining("\n")),
            EntityUtils.toString(response.getEntity(), DsmoqClient.DEFAULT_RESPONSE_CHAESET.name())
        );
    }
    public ErrorRespondedException(int statusCode, String reasonPhrase, String header, String body) {
        super(String.format("%d %s - %s", statusCode, reasonPhrase, body));
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.header = header;
        this.body = body;
    }
    /**
     * Status Code を返します。
     * @return Status Code
     */
    public int getStatusCode() {
        return this.statusCode;
    }
    /**
     * Reason Phrase を返します。
     * @return Reason Phrase
     */
    public String getReasonPhrase() {
        return this.reasonPhrase;
    }
    /**
     * ヘッダ文字列を返します。
     * @return ヘッダ文字列
     */
    public String getHeader() {
        return this.header;
    }
    /**
     * ボディ文字列を返します
     * @return ボディ文字列
     */
    public String getBody() {
        return this.body;
    }
}
