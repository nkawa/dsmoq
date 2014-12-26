package dsmoq.sdk.sample;

import dsmoq.sdk.client.DsmoqClient;
import dsmoq.sdk.request.CreateGroupParam;
import dsmoq.sdk.response.Group;

public class CreateGroupSample {

    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        try (DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key")) {
            Group group = client.createGroup(new CreateGroupParam("dummy group", "group of dummy"));
        }
    }

}
