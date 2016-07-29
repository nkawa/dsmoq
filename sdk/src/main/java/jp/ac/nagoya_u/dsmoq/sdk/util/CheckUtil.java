package jp.ac.nagoya_u.dsmoq.sdk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class CheckUtil {
    private static Marker LOG_MARKER = MarkerFactory.getMarker("SDK");
    private static Logger logger = LoggerFactory.getLogger(LOG_MARKER.toString());

    private CheckUtil() {
    }

    /**
     * 非nullチェックを行う。
     * @param x チェック対象
     * @param position チェック位置
     * @throws NullPointerException 引数がnullの場合
     */
    public static <T> void requireNotNull(T x, String position) {
        if (x == null) {
            logger.warn(LOG_MARKER, "invalid parameter - null ({})", position);
            throw new NullPointerException(String.format("invalid parameter - null (%s)", position));
        }
    }

    /**
     * 指定された値が基準値以上であることを検査する。
     * 
     * 値がnullの場合は検査を行いません。
     * @param x チェック対象
     * @param base 基準値
     * @param position チェック位置
     * @throws IllegalArgumentException 引数が基準値以上でない場合
     */
    public static <T extends Comparable<T>> void requireGreaterOrEqualOrNull(T x, T base, String position) {
        if (x != null && base.compareTo(x) > 0) {
            logger.warn(LOG_MARKER, "invalid parameter - {} is not bigger than {} ({})", x.toString(), base.toString(), position);
            throw new IllegalArgumentException(String.format("invalid parameter - %S is not bigger than %s (%s)", x.toString(), base.toString(), position));
        }
    }
}
