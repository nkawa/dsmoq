package dsmoq.sdk.sample;

import dsmoq.sdk.client.DsmoqClient;
import dsmoq.sdk.request.AddMemberParam;
import dsmoq.sdk.request.CreateGroupParam;
import dsmoq.sdk.response.Group;
import dsmoq.sdk.response.GroupAddImages;

import java.util.Arrays;
import java.io.File;

public class CreateGroupSample {

    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key");
        // グループを作成する
        Group group = client.createGroup(new CreateGroupParam("dummy group", "group of dummy"));
        // グループにメンバーを追加する
        client.addMember(group.getId(), Arrays.asList(new AddMemberParam("userId 1", 1), new AddMemberParam("userId 2", 1)));
        // グループに画像を追加し、メイン画像に設定する
        client.setPrimaryImageToGroup(group.getId(), new File("test.png"));
    }

}
