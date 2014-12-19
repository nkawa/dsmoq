
import dsmoq.sdk.client.DsmoqClient;
import dsmoq.sdk.request.*;
import dsmoq.sdk.response.*;
import dsmoq.sdk.util.*;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.Is.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class SDKTest {

    @After
    public void tearDown() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {

            List<GroupsSummary> groups = client.getGroups(new GetGroupsParam()).getResults();
            for (GroupsSummary group : groups) {
                client.deleteGroup(group.getId());
            }
        }
    }

    @AfterClass
    public static void cleaning() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            List<DatasetsSummary> s = client.getDatasets(new GetDatasetsParam()).getResults();
            List<Dataset> datasets = s.stream().map(x -> client.getDataset(x.getId())).collect(Collectors.toList());
            for (Dataset dataset : datasets) {
                dataset.getFiles().forEach(x -> client.deleteFile(dataset.getId(), x.getId()));
                client.deleteDataset(dataset.getId());
            }
        }
    }

    @Test
    public void ログインできるか1() {
        DsmoqClient.signin("http://localhost:8080", "dummy", "password");
    }

    @Test
    public void ログインできるか2() {
        DsmoqClient client = new DsmoqClient("http://localhost:8080", "dummy", "password");
        client.signin();
    }

    @Test(expected = NotAuthorizedException.class)
    public void ログアウトできるか() {
        DsmoqClient client = new DsmoqClient("http://localhost:8080", "dummy", "password");
        client.signin();
        client.signout();
        client.createDataset(new File("../../README.md"));
    }

    @Test
    public void データセットを作成して削除できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            assertThat(client.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            assertThat(summaries.size(), is(1));
            String datasetId = summaries.stream().findFirst().get().getId();
            client.deleteDataset(datasetId);
            assertThat(client.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
        }
    }

    @Test
    public void データセットを一意に特定できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            Dataset dataset = client.getDataset(datasetId);
            assertThat(dataset.getId(), is(datasetId));
            assertThat(dataset.getFiles().size(), is(1));
        }
    }

    @Test
    public void データセットにファイルを追加できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            client.addFiles(datasetId, new File("../../README.md"), new File("../../README.md"));
            Dataset dataset = client.getDataset(datasetId);
            assertThat(dataset.getFiles().size(), is(3));
        }
    }

    @Test
    public void ファイルを更新できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            Dataset dataset = client.getDataset(datasetId);
            client.updateFile(datasetId, dataset.getFiles().stream().findFirst().get().getId(), new File("../../.gitignore"));
            Dataset dataset2 = client.getDataset(datasetId);
            assertThat(dataset2.getFiles().stream().findFirst().get().getName(), is(".gitignore"));
        }
    }

    @Test
    public void ファイル情報を更新できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            Dataset dataset = client.getDataset(datasetId);
            UpdateFileMetaParam param = new UpdateFileMetaParam("HOGE.md", "Hoge");
            client.updateFileMetaInfo(datasetId, dataset.getFiles().stream().findFirst().get().getId(), param);
            Dataset dataset2 = client.getDataset(datasetId);
            DatasetFile file = dataset2.getFiles().stream().findFirst().get();
            assertThat(file.getName(), is("HOGE.md"));
            assertThat(file.getDescription(), is("Hoge"));
        }
    }

    @Test
    public void ファイルを削除できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            Dataset dataset = client.getDataset(datasetId);
            client.deleteFile(datasetId, dataset.getFiles().stream().findFirst().get().getId());
            Dataset dataset2 = client.getDataset(datasetId);
            Optional<DatasetFile> file = dataset2.getFiles().stream().findFirst();
            assertThat(file, is(Optional.empty()));
        }
    }

    @Test
    public void データセットの情報を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
            param.setName("hoge");
            param.setDescription("dummy description");
            param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
            client.updateDatasetMetaInfo(datasetId, param);
            Dataset dataset = client.getDataset(datasetId);
            assertThat(dataset.getMeta().getName(), is("hoge"));
            assertThat(dataset.getMeta().getDescription(), is("dummy description"));
        }
    }

    @Test
    public void データセットの画像を追加できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            assertThat(client.getDataset(datasetId).getImages().size(), is(1));
            client.addImagesToDataset(datasetId, new File("../../test.png"));
            assertThat(client.getDataset(datasetId).getImages().size(), is(2));
        }
    }

    @Test
    public void データセットの画像を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            String primary = client.getDataset(datasetId).getPrimaryImage();
            DatasetAddImages images = client.addImagesToDataset(datasetId, new File("../../test.png"));
            Image image = images.getImages().stream().filter(x -> !x.getId().equals(primary)).findFirst().get();
            client.setPrimaryImageToDataset(datasetId, new SetPrimaryImageParam(image.getId()));
            Dataset dataset = client.getDataset(datasetId);
            assertThat(dataset.getPrimaryImage(), is(image.getId()));
        }
    }

    @Test
    public void データセットの画像を削除できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            String primary = client.getDataset(datasetId).getPrimaryImage();
            client.addImagesToDataset(datasetId, new File("../../test.png"));
            assertThat(client.getDataset(datasetId).getImages().size(), is(2));
            client.deleteImageToDataset(datasetId, primary);
            assertThat(client.getDataset(datasetId).getImages().size(), is(1));
        }
    }

    @Test
    public void データセットの権限を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            String userId = client.getDataset(datasetId).getOwnerShips().stream().findFirst().get().getId();
            SetAccessLevelParam param = new SetAccessLevelParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1, 3);
            client.changeAccessLevel(datasetId, Arrays.asList(param));
            assertThat(client.getDataset(datasetId).getOwnerShips().stream().filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81")).findFirst().get().getAccessLevel(), is(3));
        }
    }

    @Test
    public void データセットのゲスト権限を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            client.changeGuestAccessLevel(datasetId, new SetGuestAccessLevelParam(3));
            assertThat(client.getDataset(datasetId).getPermission(), is(3));
        }
    }

    @Test
    public void ファイルをダウンロードできるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            Dataset dataset = client.getDataset(datasetId);
            String fileId = dataset.getFiles().stream().findFirst().get().getId();
            File file = client.downloadFile(datasetId, fileId, ".");
            assertThat(file.getName(), is("README.md"));
            assertThat(file.exists(), is(true));
            file.delete();
        }
    }

    @Test
    public void 保存先を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createDataset(new File("../../README.md"));
            List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
            String datasetId = summaries.stream().findFirst().get().getId();
            client.changeDatasetStorage(datasetId, new ChangeStorageParam(true, true));
            Dataset dataset = client.getDataset(datasetId);
            assertThat(dataset.getLocalState(), is(1));
            assertThat(dataset.getS3State(), is(2));
        }
    }

    @Test
    public void グループを検索できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            List<GroupsSummary> summaries = client.getGroups(new GetGroupsParam()).getResults();
            assertThat(summaries.size(), is(0));
        }
    }

    @Test
    public void グループを作成し削除できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            List<GroupsSummary> summaries = client.getGroups(new GetGroupsParam()).getResults();
            assertThat(summaries.size(), is(0));
            client.createGroup(new CreateGroupParam("hoge", "description"));
            List<GroupsSummary> summaries2 = client.getGroups(new GetGroupsParam()).getResults();
            assertThat(summaries2.size(), is(1));
            assertThat(summaries2.get(0).getName(), is("hoge"));
            assertThat(summaries2.get(0).getDescription(), is("description"));
            client.deleteGroup(summaries2.get(0).getId());
            assertThat(client.getGroups(new GetGroupsParam()).getResults().size(), is(0));
        }
    }

    @Test
    public void グループの詳細情報を取得できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.createGroup(new CreateGroupParam("hoge1", "description"));
            List<GroupsSummary> summaries = client.getGroups(new GetGroupsParam()).getResults();
            String groupId = summaries.get(0).getId();
            Group group = client.getGroup(groupId);
            assertThat(group.getId(), is(groupId));
        }
    }

    @Test
    public void グループのメンバーを取得できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            Group group = client.createGroup(new CreateGroupParam("hoge2", "description"));
            String groupId = group.getId();
            RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
            assertThat(members.getResults().size(), is(1));
            assertThat(members.getResults().get(0).getName(), is("dummy"));
        }
    }

    @Test
    public void グループの情報を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
            client.updateGroup(groupId, new UpdateGroupParam("hoge2", "description2"));
            Group group = client.getGroup(groupId);
            assertThat(group.getName(), is("hoge2"));
            assertThat(group.getDescription(), is("description2"));
        }
    }

    @Test
    public void グループに画像を追加できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
            String groupId = group.getId();
            String primary = group.getImages().get(0).getId();
            client.addImagesToGroup(groupId, new File("../../test.png"));
            assertThat(client.getGroup(groupId).getImages().size(), is(2));
        }
    }

    @Test
    public void グループのメイン画像を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
            String groupId = group.getId();
            GroupAddImages image = client.addImagesToGroup(groupId, new File("../../test.png"));
            String imageId = image.getImages().get(0).getId();
            client.setPrimaryImageToGroup(groupId, new SetPrimaryImageParam(imageId));
            assertThat(client.getGroup(groupId).getPrimaryImage(), is(imageId));
        }
    }

    @Test
    public void グループの画像を削除できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
            String groupId = group.getId();
            GroupAddImages image = client.addImagesToGroup(groupId, new File("../../test.png"));
            String imageId = image.getImages().get(0).getId();
            client.deleteImageToGroup(groupId, imageId);
            assertThat(client.getGroup(groupId).getImages().size(), is(1));
        }
    }

    @Test
    public void グループにメンバを追加できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
            client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
            RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
            assertThat(members.getResults().size(), is(2));
        }
    }

    @Test
    public void メンバのロールを変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
            client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
            client.setMemberRole(groupId, "023bfa40-e897-4dad-96db-9fd3cf001e81", new SetMemberRoleParam(2));
            RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
            assertThat(members.getResults().stream().filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81")).findFirst().get().getRole(), is(2));
        }
    }

    @Test
    public void メンバを削除できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
            client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
            client.deleteMember(groupId, "023bfa40-e897-4dad-96db-9fd3cf001e81");
            RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
            assertThat(members.getResults().size(), is(1));
        }
    }

    @Test
         public void プロフィールを取得できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            User user = client.getProfile();
            assertThat(user.getName(), is("dummy"));
        }
    }

    @Test
    public void プロフィールを変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            User user = client.getProfile();
            client.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(), user.getTitle(), "test"));
            assertThat(client.getProfile().getDescription(), is("test"));
            client.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(), user.getTitle(), "description"));
        }
    }

    @Test
    public void プロフィール画像を変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            User user = client.getProfile();
            client.updateProfileIcon(new File("../../test.png"));
        }
    }

    @Test
    public void メールアドレスを変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            client.updateEmail(new UpdateEmailParam("test2@test.jp"));
            assertThat(client.getProfile().getMailAddress(), is("test2@test.jp"));
            client.updateEmail(new UpdateEmailParam("dummy@test.jp"));
        }
    }

    @Test
    public void パスワードを変更できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "test", "password")) {
            client.changePassword(new ChangePasswordParam("password", "passw0rd"));
        }
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "test", "passw0rd")) {
            User user = client.getProfile();
            assertThat(user.getName(), is("test"));
            client.changePassword(new ChangePasswordParam("passw0rd", "password"));
        }
    }

    @Test
    public void アカウント一覧を取得できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            List<User> users = client.getAccounts();
            assertThat(users.size(), is(2));
        }
    }

    @Test
    public void タスクのステータスを取得できるか() {
        try (DsmoqClient client = DsmoqClient.signin("http://localhost:8080", "dummy", "password")) {
            Dataset dataset = client.createDataset(new File("../../README.md"));
            String datasetId = dataset.getId();
            DatasetTask task = client.changeDatasetStorage(datasetId, new ChangeStorageParam(false, true));
            TaskStatus status = client.getTaskStatus(task.getTaskId());
            assertThat(status.getStatus(), is(0));
        }
    }
}

