package jp.ac.nagoya_u.dsmoq.sdk.sample;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Range指定を用いて並列でファイルをダウンロードするサンプル */
public class DownloadParallelSample {
    /** 分割単位 (512MB) */
    public static long CHUNK_SIZE = 512L * 1024 * 1024;
    /** 最大同時ダウンロード数 */
    public static int THREAD_NUM = 3;

    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key");
        // ダウンロード対象のデータセットID、ファイルIDを指定する
        String datasetId = "dataset id";
        String fileId = "file id";
        // 分割数計算の為、全体のファイルサイズを取得する
        long size = client.getFileSize(datasetId, fileId);
        // 分割されたファイルのファイルサイズを計算
        List<Long> chunkSizes = new ArrayList<>();
        long remain = size;
        while (remain > 0) {
            chunkSizes.add(Math.min(CHUNK_SIZE, remain));
            remain -= CHUNK_SIZE;
        }
        // 分割してファイルを取得するタスクを作成
        List<Callable<File>> tasks = new ArrayList<>();
        // Range計算のためにindexが必要
        int i = 0;
        for (long chunkSize : chunkSizes) {
            final long offset = i * CHUNK_SIZE;
            final int index = i;
            tasks.add(() -> {
                return client.downloadFileWithRange(datasetId, fileId, offset, offset + chunkSize, content -> {
                    // {オリジナルファイル名}.{インデックス}でファイルを作成
                    File file = Paths.get(String.format("%s.%d", content.getName(), index)).toFile();
                    try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
                        // writeToメソッドで出力先ファイルへ対象ファイルの内容を一気に書き出す
                        content.writeTo(fos);
                    } catch (IOException e) {
                        // do something
                        throw new RuntimeException(e);
                    }
                    return file;
                });
            });
            i++;
        }
        // タスクを実行
        ExecutorService es = Executors.newFixedThreadPool(THREAD_NUM);
        try {
            List<Future<File>> results = es.invokeAll(tasks);
            for (Future<File> result : results) {
                result.get();
            }
        } catch (ExecutionException e) {
            // do something
        } catch (InterruptedException e) {
            // do something
        }
        es.shutdown();
    }
}
