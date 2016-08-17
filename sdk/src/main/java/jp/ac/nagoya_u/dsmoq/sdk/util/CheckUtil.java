package jp.ac.nagoya_u.dsmoq.sdk.util;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** 引数の妥当性検査用のユーティリティクラス */
public class CheckUtil {
    /** ログマーカー */
    private static final Marker LOG_MARKER = MarkerFactory.getMarker("SDK");

    /** ロガー */
    private static Logger logger = LoggerFactory.getLogger(LOG_MARKER.toString());

    /** メッセージ用のリソースバンドル */
    private static ResourceBundle resource = ResourceBundle.getBundle("message");

    /**
     * 指定された値が基準値以上であることを検査する。
     * 
     * 値がnullの場合は検査を行いません。
     * 
     * @param x チェック対象
     * @param base 基準値
     * @param position チェック位置
     * @throws IllegalArgumentException 引数が基準値以上でない場合
     */
    public static <T extends Comparable<T>> void requireGreaterOrEqualOrNull(T x, T base, String position) {
        if (x != null && base.compareTo(x) > 0) {
            logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_INVALID_NOT_GREATER_OR_EQUAL), x.toString(),
                    base.toString(), position);
            throw new IllegalArgumentException(
                    String.format(resource.getString(ResourceNames.LOG_INVALID_NOT_GREATER_OR_EQUAL), x.toString(),
                            base.toString(), position));
        }
    }

    /**
     * コレクションの要素に対して非空チェックを行う。
     * 
     * @param params チェック対象のコレクション
     * @param position チェック位置(String.formatでチェックに違反した要素のインデックスが入ります)
     * @throws NoSuchElementException 引数が空の場合
     */
    public static <T> void requireNotEmpty(Collection<T> params, String position) {
        if (params != null && params.isEmpty()) {
            logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_INVALID_EMPTY), position);
            throw new NoSuchElementException(
                    String.format(resource.getString(ResourceNames.ERR_INVALID_EMPTY), position));
        }
    }

    /**
     * 配列の要素に対して非空チェックを行う。
     * 
     * @param params チェック対象の配列
     * @param position チェック位置(String.formatでチェックに違反した要素のインデックスが入ります)
     * @throws NoSuchElementException 引数が空の場合
     */
    public static <T> void requireNotEmpty(T[] params, String position) {
        if (params != null && params.length == 0) {
            logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_INVALID_EMPTY), position);
            throw new NoSuchElementException(
                    String.format(resource.getString(ResourceNames.ERR_INVALID_EMPTY), position));
        }
    }

    /**
     * 非nullチェックを行う。
     * 
     * @param x チェック対象
     * @param position チェック位置
     * @throws NullPointerException 引数がnullの場合
     */
    public static <T> void requireNotNull(T x, String position) {
        if (x == null) {
            logger.warn(LOG_MARKER, resource.getString(ResourceNames.LOG_INVALID_NULL), position);
            throw new NullPointerException(String.format(resource.getString(ResourceNames.ERR_INVALID_NULL), position));
        }
    }

    /**
     * コレクションの要素に対して非nullチェックを行う。
     * 
     * @param params チェック対象のコレクション
     * @param position チェック位置(String.formatでチェックに違反した要素のインデックスが入ります)
     * @throws NullPointerException 引数がnullの場合
     */
    public static <T> void requireNotNullAll(Collection<T> params, String position) {
        int i = 0;
        for (T param : params) {
            requireNotNull(param, String.format(position, i));
            i++;
        }
    }

    /**
     * 配列の要素に対して非nullチェックを行う。
     * 
     * @param params チェック対象の配列
     * @param position チェック位置(String.formatでチェックに違反した要素のインデックスが入ります)
     * @throws NullPointerException 引数がnullの場合
     */
    public static <T> void requireNotNullAll(T[] params, String position) {
        int i = 0;
        for (T param : params) {
            requireNotNull(param, String.format(position, i));
            i++;
        }
    }

    /**
     * デフォルトコンストラクタ
     *
     * 非公開とし、継承及びインスタンス化を抑制する。
     */
    private CheckUtil() {
        // do nothing
    }
}
