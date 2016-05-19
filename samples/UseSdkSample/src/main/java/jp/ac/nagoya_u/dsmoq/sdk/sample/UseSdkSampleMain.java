package jp.ac.nagoya_u.dsmoq.sdk.sample;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.*;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UseSdkSampleMain {
    private static final String CONSUMER_KEY = "enter your client_id";
    private static final String SECRET_KEY = "enter your client_secret";

    // 参照するデータセットのID
    private static final String TEST_DATASET_ID = "enter test Dataset ID";

    // 作成するグループ名
    private  static final String TEST_CREATE_GROUP_NAME = "SDK Group";

    // 読み込む属性のCSVファイル
    private static final String TEST_ATTR_FILE = "attr.csv";

    public static void main(String[] args) {
        DsmoqClient client =  DsmoqClient.create("http://localhost:8080", CONSUMER_KEY, SECRET_KEY);

        try {
            client.getDataset(TEST_DATASET_ID);
        } catch (jp.ac.nagoya_u.dsmoq.sdk.util.NotAuthorizedException e) {
            System.err.println("check CONSUMER_KEY and SECRET_KEY value");
            return;
        } catch (jp.ac.nagoya_u.dsmoq.sdk.util.NotFoundException e) {
            System.err.println("check TEST_DATASET_ID value");
            return;
        }

        // データセットの操作
        useDatasetApi(client);

        // グループの操作
        useGroupApi(client);

        System.out.println("finish.");
    }

    private static void useDatasetApi(DsmoqClient client) {
        // Dataset の取得 [get:/api/datasets/:datasetId]
        Dataset dataset = dataset = client.getDataset(TEST_DATASET_ID);
        printout("",
                "=== getDataset [get:/api/datasets/:datasetId] ===",
                "  dataset     : " + dataset.toString(),
                "  id          : " + dataset.getId(),
                "  fileSize    : " + dataset.getFilesSize(),
                "  files.size  : " + dataset.getFilesCount());

        // 属性の設定(インポート) [post:/api/datasets/:datasetId/attributes/import]
        File attrFile = new File(TEST_ATTR_FILE);

        client.importAttribute(TEST_DATASET_ID, attrFile);

        dataset = client.getDataset(TEST_DATASET_ID);
        printout("",
                "=== importAttribute [post:/api/datasets/:datasetId/attributes/import] ===",
                "  dataset     : " + dataset.toString());
        printout(dataset.getMeta().getAttributes().stream().map(keyPair ->
                        "  attr        : name = " + keyPair.getName() + ", value = " + keyPair.getValue()));

        // データセットの情報を更新 [put:/api/datasets/:datasetId/metadata]
        UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
        param.setName(dataset.getMeta().getName());
        param.setDescription("SDKからDescriptionを更新。更新日時： " + ZonedDateTime.now().toString());
        param.setLicense(dataset.getMeta().getLicense());
        List<Attribute> attrs = dataset.getMeta().getAttributes().stream().map(keyPair ->
                new Attribute(keyPair.getName(), keyPair.getValue())).collect(Collectors.toList());
        attrs.add(new Attribute("追加属性", ZonedDateTime.now().toString()));
        param.setAttributes(attrs);

        client.updateDatasetMetaInfo(TEST_DATASET_ID,  param);

        dataset = client.getDataset(TEST_DATASET_ID);
        printout("",
                "=== updateDatasetMetaInfo [put:/api/datasets/:datasetId/metadata] ===",
                "  dataset     : " + dataset.toString(),
                "  name        : " + dataset.getMeta().getName(),
                "  description : " + dataset.getMeta().getDescription()
        );
        printout(dataset.getMeta().getAttributes().stream().map(keyPair ->
                "  attr       : name = " + keyPair.getName() + ", value = " + keyPair.getValue() +
                        (keyPair.getName().equals("追加属性") ?
                                "  (SDK(DsmoqClient#updateDatasetMetaInfo)で追加した属性)" : ""))
        );

    }

    private static void useGroupApi(DsmoqClient client) {
        String sdkGroupId;
        Group group;

        // グループ一覧取得 [get:/api/group]
        GetGroupsParam param = new GetGroupsParam();
        param.setQuery(Optional.of(TEST_CREATE_GROUP_NAME));

        RangeSlice<GroupsSummary> groupSummary = client.getGroups(param);

        printout("",
                "=== getGroups [post:/api/groups] ===",
                "  groups      : " + groupSummary.toString(),
                "  summary     : " + groupSummary.getSummary(),
                "  [count]     : " + groupSummary.getResults().size()
        );

        // テスト用のグループ名が存在しなければ作成
        if (groupSummary.getResults().isEmpty() ||  groupSummary.getResults().size() <= 0) {

            // グループ作成 [post:/api/groups]
            CreateGroupParam createParam = new CreateGroupParam();
            createParam.setName(TEST_CREATE_GROUP_NAME);
            createParam.setDescription("SDKから作成したグループ。作成日時：" + ZonedDateTime.now().toString());

            group = client.createGroup(createParam);

            sdkGroupId = group.getId();
            printout("",
                    "=== createGroup [post:/api/groups] ===",
                    "  group       : " + group.toString(),
                    "  id          : " + sdkGroupId,
                    "  name        : " + group.getName(),
                    "  description : " + group.getDescription(),
                    "  role        : " + group.getRole()
            );
        } else {
            sdkGroupId = groupSummary.getResults().get(0).getId();
        }

        // グループ詳細を取得 [get://api/groups/:groupId]
        group = client.getGroup(sdkGroupId);

        printout("",
                "=== getGroup [get://api/groups/:groupId] ===",
                "  group       : " + group.toString(),
                "  id          : " + group.getId(),
                "  name        : " + group.getName(),
                "  description : " + group.getDescription(),
                "  role        : " + group.getRole()
        );

        // グループのメンバー一覧を取得 [get://api/groups/:groupId/members]
        GetMembersParam membersParam = new GetMembersParam(
                Optional.of(100),
                Optional.of(0));

        RangeSlice<MemberSummary> memberSummary = client.getMembers(sdkGroupId, membersParam);

        printout("",
                "=== getMembers [get:/api/groups/:groupId/members] ===",
                "  members     : " + memberSummary.toString(),
                "  summary     : " + memberSummary.getSummary(),
                "  [count]     : " + memberSummary.getResults().size()
        );
        memberSummary.getResults().forEach(x -> {
            printout("  -- member   : " + x.toString(),
                    "     id       : " + x.getId(),
                    "     name     : " + x.getName(),
                    "     full_name: " + x.getFullname(),
                    "     role     : " + x.getRole()
            );
        });

    }

    private static void printout(String... strs) {
        printout(Arrays.stream(strs));
    }

    private static void printout(Stream stream) {
        stream.forEach(x -> System.out.println(x));
    }

}