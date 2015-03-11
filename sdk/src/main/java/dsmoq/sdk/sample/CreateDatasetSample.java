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
        // データセットに画像を追加する
        DatasetAddImages images = client.addImagesToDataset(dataset.getId(), image);
        String imageId = images.getImages().get(0).getId();
        // 追加した画像をメイン画像に設定する
        client.setPrimaryImageToDataset(dataset.getId(), new SetPrimaryImageParam(imageId));
        // ユーザーのアクセス権をOwnerとして追加する
        List<SetAccessLevelParam> accesses = Arrays.asList(new SetAccessLevelParam("user id 1", 1, 3), new SetAccessLevelParam("user id 2", 1, 3));
        client.changeAccessLevel(dataset.getId(), accesses);
    }

}
