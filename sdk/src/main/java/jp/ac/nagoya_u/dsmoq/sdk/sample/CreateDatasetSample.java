package jp.ac.nagoya_u.dsmoq.sdk.sample;

import java.io.File;
import java.util.*;

import jp.ac.nagoya_u.dsmoq.sdk.client.Consts;
import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetAccessLevelParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;

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
        List<SetAccessLevelParam> accesses = Arrays.asList(
                new SetAccessLevelParam("05a8456e-7aad-c84e-bd4e-6cb255f9df9e", Consts.OwnerType.User, Consts.AccessLevel.Owner),
                new SetAccessLevelParam("215316c8-283b-27af-57a5-654acddcf2f5", Consts.OwnerType.User, Consts.AccessLevel.Owner));
        client.changeAccessLevel(dataset.getId(), accesses);
    }

}
