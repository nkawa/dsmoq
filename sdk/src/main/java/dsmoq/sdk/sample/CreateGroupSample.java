package dsmoq.sdk.sample;

import dsmoq.sdk.client.DsmoqClient;
import dsmoq.sdk.client.GroupClient;
import dsmoq.sdk.request.CreateGroupParam;
import dsmoq.sdk.response.Group;

public class CreateGroupSample {

    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key");
        GroupClient gClient = new GroupClient(client);
        Group group = gClient.createGroup(new CreateGroupParam("dummy group", "group of dummy"));
    }

}
