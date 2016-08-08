package jp.ac.nagoya_u.dsmoq.sdk.sample;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;

import java.io.*;
import java.nio.file.Paths;

/** プログレス表示付きのファイルダウンロードのサンプル */
public class DownloadProgressSample {
    /** 一度に読み込む最大のバイト数 */
    public static int BUFFER_SIZE = 1024 * 1024;
    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key");
        // ダウンロード対象のデータセットID、ファイルIDを指定する
        String datasetId = "dataset id";
        String fileId = "file id";
        // 完了割合表示の為、全体のファイルサイズを取得する
        long size = client.getFileSize(datasetId, fileId);
        // ファイルのダウンロードを開始する
        File file = client.downloadFile(datasetId, fileId, content -> {
            // 設定されているファイル名はcontent.getName()で取得できる
            File outputFile = Paths.get(content.getName()).toFile();
            // ファイル出力用のOutputStreamを作成
            try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(outputFile), BUFFER_SIZE)) {
                // getContentメソッドでファイルデータを表すInputStreamが取得できる
                // 本例のように逐次プログレスを更新したい場合等に用いるとよい
                BufferedInputStream is = new BufferedInputStream(content.getContent(), BUFFER_SIZE);
                // 書き込み用バッファ
                byte[] buffer = new byte[BUFFER_SIZE];
                // 既に書き込んだバイト数
                long wroteSize = 0;
                while (true) {
                    // InputStreamから読み込み、OutputStreamに書き込んでいく
                    int read = is.read(buffer, 0, BUFFER_SIZE);
                    if (read < 0) {
                        System.out.println("read finished.");
                        break;
                    }
                    fos.write(buffer, 0, read);
                    wroteSize += read;
                    System.out.printf("wrote %d bytes (%f %%)%n", wroteSize, ((double) wroteSize * 100) / size);
                }
                System.out.printf("write finished (%d bytes)%n", wroteSize);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return outputFile;
        });
        System.out.printf("write finished to %s%n", file.getName());
    }
}
