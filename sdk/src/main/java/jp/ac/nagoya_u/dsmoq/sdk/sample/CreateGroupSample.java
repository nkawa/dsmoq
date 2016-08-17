package jp.ac.nagoya_u.dsmoq.sdk.sample;

import java.io.File;
import java.util.Arrays;

import jp.ac.nagoya_u.dsmoq.sdk.client.Consts;
import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.AddMemberParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.CreateGroupParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.Group;

public class CreateGroupSample {

    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key");
        // グループを作成する
        Group group = client.createGroup(new CreateGroupParam("dummy group", "group of dummy"));
        // グループにメンバーを追加する
        client.addMember(group.getId(),
                Arrays.asList(new AddMemberParam("eee8ea92-43a2-e4df-fa24-3ce7e9a3c857", Consts.Role.Member),
                        new AddMemberParam("aaf3c0fc-091f-4dfc-a802-852819977162", Consts.Role.Member)));
        // グループに画像を追加し、メイン画像に設定する
        client.setPrimaryImageToGroup(group.getId(), new File("test.png"));
    }

}
