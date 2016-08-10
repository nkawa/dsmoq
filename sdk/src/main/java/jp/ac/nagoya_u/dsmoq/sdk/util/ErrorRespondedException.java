package jp.ac.nagoya_u.dsmoq.sdk.util;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.util.EntityUtils;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * サーバ側からエラーレスポンスが返ってきたことを表す例外
 */
public class ErrorRespondedException extends Exception {
    /** デフォルトのレスポンスボディ文字コード */
    private static final Charset DEFAULT_RESPONSE_CHAESET = StandardCharsets.UTF_8;

    /** HTTP Response Body */
    private String body;
    /** HTTP Response Header */
    private String header;
    /** HTTP Response Reason Phrase */
    private String reasonPhrase;
    /** HTTP Response Status Code */
    private int statusCode;

    /**
     * 指定されたHttpResponseを用いて、この例外を構築します。
     * 
     * @param response 元となるHttpResponse
     */
    public ErrorRespondedException(HttpResponse response) throws IOException {
        this(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase(),
                Arrays.stream(response.getAllHeaders()).map(Header::toString).collect(Collectors.joining("\n")),
                response.getEntity() == null ? ""
                        : EntityUtils.toString(response.getEntity(), DEFAULT_RESPONSE_CHAESET.name()));
    }

    /**
     * 指定された値を用いて、この例外を構築します。
     * 
     * @param statusCode Status Code
     * @param reasonPhrase Reason Phrase
     * @param header Response Header
     * @param body Response Body
     */
    public ErrorRespondedException(int statusCode, String reasonPhrase, String header, String body) {
        super(String.format("%d %s - %s", statusCode, reasonPhrase, body));
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.header = header;
        this.body = body;
    }

    /**
     * ボディ文字列を返します
     * 
     * @return ボディ文字列
     */
    public String getBody() {
        return this.body;
    }

    /**
     * ヘッダ文字列を返します。
     * 
     * @return ヘッダ文字列
     */
    public String getHeader() {
        return this.header;
    }

    /**
     * Reason Phrase を返します。
     * 
     * @return Reason Phrase
     */
    public String getReasonPhrase() {
        return this.reasonPhrase;
    }

    /**
     * Status Code を返します。
     * 
     * @return Status Code
     */
    public int getStatusCode() {
        return this.statusCode;
    }
}
