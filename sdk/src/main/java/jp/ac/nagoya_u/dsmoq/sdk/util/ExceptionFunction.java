package jp.ac.nagoya_u.dsmoq.sdk.util;

/**
 * 例外を送出し得る関数
 * @param <R> 関数の入力の型
 * @param <R> 関数の結果の型
 */
@FunctionalInterface
public interface ExceptionFunction<T, R> {
    /**
     * 指定された引数にこの関数を適用する。
     * @param x 関数の引数
     * @return 関数の結果
     * @throws Exception 関数が例外を送出した場合
     */
   R apply(T x) throws Exception;
}

