package jp.ac.nagoya_u.dsmoq.sdk.util;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;

/**
 * レスポンス変換関数
 * 
 * @param <R> レスポンス変換後の型
 */
@FunctionalInterface
public interface ResponseFunction<R> {
    /**
     * レスポンス変換関数
     * 
     * @param res HTTPレスポンス
     * @return 変換結果
     * @throws IOException レスポンスからのデータの取得に失敗した場合
     * @throws HttpException レスポンスがHTTPレスポンスとして不正な場合
     */
    R apply(HttpResponse res) throws IOException, HttpException;
}
