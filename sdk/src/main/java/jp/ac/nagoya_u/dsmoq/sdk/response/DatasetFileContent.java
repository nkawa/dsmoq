package jp.ac.nagoya_u.dsmoq.sdk.response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * データセットから取得したファイルを表します。
 */
public interface DatasetFileContent {
    /**
     * ファイルの内容を取得します。
     * 
     * 単純にファイルの内容を全て出力する場合にはwriteToメソッドが便利です。
     * 
     * @return ファイルの内容を表すストリーム
     * @throws IOException 入出力エラーが発生した場合
     */
    InputStream getContent() throws IOException;

    /**
     * ファイルに設定されているファイル名を取得します。
     * 
     * @return 設定されているファイル名、ない場合null
     */
    String getName();

    /**
     * ファイルの内容を、指定されたストリームへ書き込みます。
     * 
     * 単純にファイルの内容を全て出力する場合に使用します。
     * 複雑な処理を行いたい場合にはgetContentメソッドを利用してください。
     * 
     * @param os 出力先ストリーム
     * @throws IOException 入出力エラーが発生した場合
     */
    void writeTo(OutputStream os) throws IOException;
}
