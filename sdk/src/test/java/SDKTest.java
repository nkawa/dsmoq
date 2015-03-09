
import dsmoq.sdk.client.DatasetClient;
import dsmoq.sdk.client.DsmoqClient;
import dsmoq.sdk.client.GroupClient;
import dsmoq.sdk.client.ProfileClient;
import dsmoq.sdk.request.*;
import dsmoq.sdk.response.*;
import dsmoq.sdk.util.*;
import org.joda.time.DateTime;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.Is.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class SDKTest {

    @After
    public void tearDown() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        List<GroupsSummary> groups = gClient.getGroups(new GetGroupsParam()).getResults();
        for (GroupsSummary group : groups) {
            gClient.deleteGroup(group.getId());
        }
    }

    @AfterClass
    public static void cleaning() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        List<DatasetsSummary> s = dClient.getDatasets(new GetDatasetsParam()).getResults();
        List<Dataset> datasets = s.stream().map(x -> dClient.getDataset(x.getId())).collect(Collectors.toList());
        for (Dataset dataset : datasets) {
            dataset.getFiles().forEach(x -> dClient.deleteFile(dataset.getId(), x.getId()));
            dClient.deleteDataset(dataset.getId());
        }
    }

    @Test
    public void データセットを作成して削除できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        assertThat(dClient.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        assertThat(summaries.size(), is(1));
        String datasetId = summaries.stream().findFirst().get().getId();
        dClient.deleteDataset(datasetId);
        assertThat(dClient.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
    }

    @Test
    public void データセットを一意に特定できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        Dataset dataset = dClient.getDataset(datasetId);
        assertThat(dataset.getId(), is(datasetId));
        assertThat(dataset.getFiles().size(), is(1));
    }

    @Test
    public void データセットにファイルを追加できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        dClient.addFiles(datasetId, new File("../../README.md"), new File("../../README.md"));
        Dataset dataset = dClient.getDataset(datasetId);
        assertThat(dataset.getFiles().size(), is(3));
    }

    @Test
    public void ファイルを更新できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        Dataset dataset = dClient.getDataset(datasetId);
        dClient.updateFile(datasetId, dataset.getFiles().stream().findFirst().get().getId(), new File("../../.gitignore"));
        Dataset dataset2 = dClient.getDataset(datasetId);
        assertThat(dataset2.getFiles().stream().findFirst().get().getName(), is(".gitignore"));
    }

    @Test
    public void ファイル情報を更新できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        Dataset dataset = dClient.getDataset(datasetId);
        UpdateFileMetaParam param = new UpdateFileMetaParam("HOGE.md", "Hoge");
        dClient.updateFileMetaInfo(datasetId, dataset.getFiles().stream().findFirst().get().getId(), param);
        Dataset dataset2 = dClient.getDataset(datasetId);
        DatasetFile file = dataset2.getFiles().stream().findFirst().get();
        assertThat(file.getName(), is("HOGE.md"));
        assertThat(file.getDescription(), is("Hoge"));
    }

    @Test
    public void ファイルを削除できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        Dataset dataset = dClient.getDataset(datasetId);
        dClient.deleteFile(datasetId, dataset.getFiles().stream().findFirst().get().getId());
        Dataset dataset2 = dClient.getDataset(datasetId);
        Optional<DatasetFile> file = dataset2.getFiles().stream().findFirst();
        assertThat(file, is(Optional.empty()));
    }

    @Test
    public void データセットの情報を変更できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
        param.setName("hoge");
        param.setDescription("dummy description");
        param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
        dClient.updateDatasetMetaInfo(datasetId, param);
        Dataset dataset = dClient.getDataset(datasetId);
        assertThat(dataset.getMeta().getName(), is("hoge"));
        assertThat(dataset.getMeta().getDescription(), is("dummy description"));
    }

    @Test
    public void データセットの画像を追加できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        assertThat(dClient.getDataset(datasetId).getImages().size(), is(1));
        dClient.addImagesToDataset(datasetId, new File("../../test.png"));
        assertThat(dClient.getDataset(datasetId).getImages().size(), is(2));
    }

    @Test
    public void データセットの画像を変更できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        String primary = dClient.getDataset(datasetId).getPrimaryImage();
        DatasetAddImages images = dClient.addImagesToDataset(datasetId, new File("../../test.png"));
        Image image = images.getImages().stream().filter(x -> !x.getId().equals(primary)).findFirst().get();
        dClient.setPrimaryImageToDataset(datasetId, new SetPrimaryImageParam(image.getId()));
        Dataset dataset = dClient.getDataset(datasetId);
        assertThat(dataset.getPrimaryImage(), is(image.getId()));
    }

    @Test
    public void データセットの画像を削除できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        String primary = dClient.getDataset(datasetId).getPrimaryImage();
        dClient.addImagesToDataset(datasetId, new File("../../test.png"));
        assertThat(dClient.getDataset(datasetId).getImages().size(), is(2));
        dClient.deleteImageToDataset(datasetId, primary);
        assertThat(dClient.getDataset(datasetId).getImages().size(), is(1));
    }

    @Test
    public void データセットの権限を変更できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        String userId = dClient.getDataset(datasetId).getOwnerShips().stream().findFirst().get().getId();
        SetAccessLevelParam param = new SetAccessLevelParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1, 3);
        dClient.changeAccessLevel(datasetId, Arrays.asList(param));
        assertThat(dClient.getDataset(datasetId).getOwnerShips().stream().filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81")).findFirst().get().getAccessLevel(), is(3));
    }

    @Test
    public void データセットのゲスト権限を変更できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        dClient.changeGuestAccessLevel(datasetId, new SetGuestAccessLevelParam(3));
        assertThat(dClient.getDataset(datasetId).getPermission(), is(3));
    }

    @Test
    public void ファイルをダウンロードできるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        Dataset dataset = dClient.getDataset(datasetId);
        String fileId = dataset.getFiles().stream().findFirst().get().getId();
        File file = dClient.downloadFile(datasetId, fileId, ".");
        assertThat(file.getName(), is("README.md"));
        assertThat(file.exists(), is(true));
        file.delete();
    }

    @Test
    public void 保存先を変更できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        dClient.createDataset(true, false, new File("../../README.md"));
        List<DatasetsSummary> summaries = dClient.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        dClient.changeDatasetStorage(datasetId, new ChangeStorageParam(true, true));
        Dataset dataset = dClient.getDataset(datasetId);
        assertThat(dataset.getLocalState(), is(1));
        assertThat(dataset.getS3State(), is(2));
    }

    @Test
    public void グループを検索できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        List<GroupsSummary> summaries = gClient.getGroups(new GetGroupsParam()).getResults();
        assertThat(summaries.size(), is(0));
    }

    @Test
    public void グループを作成し削除できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        List<GroupsSummary> summaries = gClient.getGroups(new GetGroupsParam()).getResults();
        assertThat(summaries.size(), is(0));
        gClient.createGroup(new CreateGroupParam("hoge", "description"));
        List<GroupsSummary> summaries2 = gClient.getGroups(new GetGroupsParam()).getResults();
        assertThat(summaries2.size(), is(1));
        assertThat(summaries2.get(0).getName(), is("hoge"));
        assertThat(summaries2.get(0).getDescription(), is("description"));
        gClient.deleteGroup(summaries2.get(0).getId());
        assertThat(gClient.getGroups(new GetGroupsParam()).getResults().size(), is(0));
    }

    @Test
    public void グループの詳細情報を取得できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        gClient.createGroup(new CreateGroupParam("hoge1", "description"));
        List<GroupsSummary> summaries = gClient.getGroups(new GetGroupsParam()).getResults();
        String groupId = summaries.get(0).getId();
        Group group = gClient.getGroup(groupId);
        assertThat(group.getId(), is(groupId));
    }

    @Test
    public void グループのメンバーを取得できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        Group group = gClient.createGroup(new CreateGroupParam("hoge2", "description"));
        String groupId = group.getId();
        RangeSlice<MemberSummary> members = gClient.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().size(), is(1));
        assertThat(members.getResults().get(0).getName(), is("dummy"));
    }

    @Test
    public void グループの情報を変更できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        String groupId = gClient.createGroup(new CreateGroupParam("hoge", "description")).getId();
        gClient.updateGroup(groupId, new UpdateGroupParam("hoge2", "description2"));
        Group group = gClient.getGroup(groupId);
        assertThat(group.getName(), is("hoge2"));
        assertThat(group.getDescription(), is("description2"));
    }

    @Test
    public void グループに画像を追加できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        Group group = gClient.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        gClient.addImagesToGroup(groupId, new File("../../test.png"));
        assertThat(gClient.getGroup(groupId).getImages().size(), is(2));
    }

    @Test
    public void グループのメイン画像を変更できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        Group group = gClient.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        GroupAddImages image = gClient.addImagesToGroup(groupId, new File("../../test.png"));
        String imageId = image.getImages().get(0).getId();
        gClient.setPrimaryImageToGroup(groupId, new SetPrimaryImageParam(imageId));
        assertThat(gClient.getGroup(groupId).getPrimaryImage(), is(imageId));
    }

    @Test
    public void グループの画像を削除できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        Group group = gClient.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        GroupAddImages image = gClient.addImagesToGroup(groupId, new File("../../test.png"));
        String imageId = image.getImages().get(0).getId();
        gClient.deleteImageToGroup(groupId, imageId);
        assertThat(gClient.getGroup(groupId).getImages().size(), is(1));
    }

    @Test
    public void グループにメンバを追加できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        String groupId = gClient.createGroup(new CreateGroupParam("hoge", "description")).getId();
        gClient.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        RangeSlice<MemberSummary> members = gClient.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().size(), is(2));
    }

    @Test
    public void メンバのロールを変更できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        String groupId = gClient.createGroup(new CreateGroupParam("hoge", "description")).getId();
        gClient.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        gClient.setMemberRole(groupId, "023bfa40-e897-4dad-96db-9fd3cf001e81", new SetMemberRoleParam(2));
        RangeSlice<MemberSummary> members = gClient.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().stream().filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81")).findFirst().get().getRole(), is(2));
    }

    @Test
    public void メンバを削除できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        String groupId = gClient.createGroup(new CreateGroupParam("hoge", "description")).getId();
        gClient.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        gClient.deleteMember(groupId, "023bfa40-e897-4dad-96db-9fd3cf001e81");
        RangeSlice<MemberSummary> members = gClient.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().size(), is(1));
    }

    @Test
    public void プロフィールを取得できるか() {
        DsmoqClient client = create();
        ProfileClient pClient = new ProfileClient(client);
        User user = pClient.getProfile();
        assertThat(user.getName(), is("dummy"));
    }

    @Test
    public void プロフィールを変更できるか() {
        DsmoqClient client = create();
        ProfileClient pClient = new ProfileClient(client);
        User user = pClient.getProfile();
        pClient.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(), user.getTitle(), "test"));
        assertThat(pClient.getProfile().getDescription(), is("test"));
        pClient.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(), user.getTitle(), "description"));
    }

    @Test
    public void プロフィール画像を変更できるか() {
        DsmoqClient client = create();
        ProfileClient pClient = new ProfileClient(client);
        User user = pClient.getProfile();
        pClient.updateProfileIcon(new File("../../test.png"));
    }

    @Test
    public void メールアドレスを変更できるか() {
        DsmoqClient client = create();
        ProfileClient pClient = new ProfileClient(client);
        pClient.updateEmail(new UpdateEmailParam("test2@test.jp"));
        assertThat(pClient.getProfile().getMailAddress(), is("test2@test.jp"));
        pClient.updateEmail(new UpdateEmailParam("dummy@test.jp"));
    }

    @Test
    public void パスワードを変更できるか() {
        DsmoqClient client = create();
        ProfileClient pClient1 = new ProfileClient(client);
        pClient1.changePassword(new ChangePasswordParam("password", "passw0rd"));

        ProfileClient pClient2 = new ProfileClient(client);
        pClient2.changePassword(new ChangePasswordParam("passw0rd", "password"));
    }

    @Test
    public void アカウント一覧を取得できるか() {
        DsmoqClient client = create();
        List<User> users = client.getAccounts();
        assertThat(users.size(), is(2));
    }

    @Test
    public void タスクのステータスを取得できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        Dataset dataset = dClient.createDataset(true, false, new File("../../README.md"));
        String datasetId = dataset.getId();
        DatasetTask task = dClient.changeDatasetStorage(datasetId, new ChangeStorageParam(false, true));
        TaskStatus status = client.getTaskStatus(task.getTaskId());
        assertThat(status.getStatus(), is(0));
    }

    @Test
    public void データセットの画像一覧を取得できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        Dataset dataset = dClient.createDataset(true, false, new File("../../README.md"));
        String datasetId = dataset.getId();
        dClient.addImagesToDataset(datasetId, new File("../../test.png"));
        RangeSlice<DatasetGetImage> images = dClient.getDatasetImage(datasetId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(2));
    }

    @Test
    public void アクセス権限一覧を取得できるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        Dataset dataset = dClient.createDataset(true, false, new File("../../README.md"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetOwnership> images = dClient.getAccessLevel(datasetId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(1));
    }

    @Test
    public void データセットをコピーできるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        Dataset dataset = dClient.createDataset(true, false, new File("../../README.md"));
        String datasetId = dataset.getId();
        String copiedDatasetId = dClient.copyDataset(datasetId);
        Dataset copiedDataset = dClient.getDataset(copiedDatasetId);
        assertThat(copiedDataset.getId(), is(copiedDatasetId));
    }

    @Test
    public void AttributeをImportできるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        Dataset dataset = dClient.createDataset(true, false, new File("../../README.md"));
        String datasetId = dataset.getId();
        dClient.importAttribute(datasetId, new File("../../test.csv"));
        Dataset dataset2 = dClient.getDataset(datasetId);
        assertThat(dataset2.getMeta().getAttributes().size(), is(2));
    }

    @Test
    public void AttributeをExportできるか() {
        DsmoqClient client = create();
        DatasetClient dClient = new DatasetClient(client);
        Dataset dataset = dClient.createDataset(true, false, new File("../../README.md"));
        String datasetId = dataset.getId();
        dClient.importAttribute(datasetId, new File("../../test.csv"));
        File file = dClient.exportAttribute(datasetId, ".");
        assertThat(file.exists(), is(true));
        file.delete();
    }

    @Test
    public void グループの画像一覧を取得できるか() {
        DsmoqClient client = create();
        GroupClient gClient = new GroupClient(client);
        Group group = gClient.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        gClient.addImagesToGroup(groupId, new File("../../test.png"));
        RangeSlice<GroupGetImage> images = gClient.getGroupImage(groupId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(2));
    }

    @Test
    public void 統計情報を取得できるか() {
        DsmoqClient client = create();
        List<StatisticsDetail> stats = client.getStatistics(new StatisticsParam(Optional.of(new DateTime()), Optional.of(new DateTime())));
        assertThat(stats.size(), is(0));
    }

    public static DsmoqClient create() {
        return DsmoqClient.create("http://localhost:8080", "5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212ca", "dc9765e63b2b469a7bfb611fad8a10f2394d2b98b7a7105078356ec2a74164ea");
    }
}

