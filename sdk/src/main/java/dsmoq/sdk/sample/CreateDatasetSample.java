package dsmoq.sdk.sample;

import java.io.File;
import java.util.*;

import dsmoq.sdk.client.DsmoqClient;
import dsmoq.sdk.request.SetAccessLevelParam;
import dsmoq.sdk.request.SetPrimaryImageParam;
import dsmoq.sdk.response.*;

public class CreateDatasetSample {

    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key");
        // データセットを作成する
        Dataset dataset = client.createDataset(true, false, new File("test.txt"), new File("test.csv"));
        File image = new File("test.png");
        // データセットに画像を追加し、メイン画像に設定する
        client.setPrimaryImageToDataset(dataset.getId(), image);
        // ユーザーのアクセス権をOwnerとして追加する
        List<SetAccessLevelParam> accesses = Arrays.asList(new SetAccessLevelParam("user id 1", 1, 3), new SetAccessLevelParam("user id 2", 1, 3));
        client.changeAccessLevel(dataset.getId(), accesses);
    }

}
