package jp.ac.nagoya_u.dsmoq.sdk.util;

/**
 * 例外を送出し得るサプライヤ
 * @param <T> このサプライヤから提供される結果の型
 */
@FunctionalInterface
public interface ExceptionSupplier<T> {
    /**
     * 結果を取得するする。
     * @return 結果
     * @throws Exception サプライヤが例外を送出した場合
     */
   T get() throws Exception;
}


