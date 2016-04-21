package jp.ac.nagoya_u.dsmoq.sdk.sample;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.response.Dataset;

import java.io.File;

public class Main {
    private static final String CONSUMER_KEY = "ad96471ebc6b3f7369e50ab5d4a21cef28621eb6c2cf467cc7e490b03b490aba";
    private static final String SECRET_KEY = "8d55750cfde9d33176d97ce02ffc83776d9d28a00328a881dabcda9ac13ab4ae";

    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", CONSUMER_KEY, SECRET_KEY);
        // データセットを作成する
//        Dataset dataset = client.createDataset(true, false, new File("テスト.txt"), new File("test.csv"));
        Dataset dataset = client.createDataset("サンプル", true, false);
        final String dataSetId = dataset.getId();

        // データセットにファイルを追加
        client.addFiles(dataSetId, new File("test.csv"));
        client.addFiles(dataSetId, new File("テスト.txt"));

        // データセットに画像を追加し、メイン画像に設定する
        File image = new File("test.png");
        client.setPrimaryImageToDataset(dataSetId, image);
    }

}
