package jp.ac.nagoya_u.dsmoq.sdk.response;

import java.io.IOException;
import java.io.OutputStream;

/**
 * データセットから取得したファイルを表します。
 */
public interface DatasetFileContent {
    /**
     * 取得したファイルに設定されているファイル名を取得します。
     * @return 設定されているファイル名、ない場合null
     */
    String getName();
    /**
     * 取得したファイルの内容を、指定されたOutputStreamへ書き込みます。
     * @param os 出力先Stream
     * @throws IOException 入出力エラーが発生した場合
     */
    void writeTo(OutputStream os) throws IOException;
}
