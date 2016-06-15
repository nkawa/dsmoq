
import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.*;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;
import jp.ac.nagoya_u.dsmoq.sdk.util.*;
import org.joda.time.DateTime;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.Is.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class SDKTest {

    @After
    public void tearDown() {
        DsmoqClient client = create();
        List<GroupsSummary> groups = client.getGroups(new GetGroupsParam(
            Optional.empty(),
            Optional.of("023bfa40-e897-4dad-96db-9fd3cf001e79"),
            Optional.empty(),
            Optional.empty()
        )).getResults();
        for (GroupsSummary group : groups) {
            try {
                client.deleteGroup(group.getId());
            } catch (Exception e) {
                // do nothing
            }
        }
        List<DatasetsSummary> datasets = client.getDatasets(new GetDatasetsParam()).getResults();
        for (DatasetsSummary dataset : datasets) {
            RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
            try {
                files.getResults().stream().forEach(x -> client.deleteFile(dataset.getId(), x.getId()));
                client.deleteDataset(dataset.getId());
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    @Test
    public void データセットを作成して削除できるか() {
        DsmoqClient client = create();
        assertThat(client.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        assertThat(summaries.size(), is(1));
        String datasetId = summaries.stream().findFirst().get().getId();
        client.deleteDataset(datasetId);
        assertThat(client.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
    }

    @Test
    public void データセットを一意に特定できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        Dataset dataset = client.getDataset(datasetId);
        assertThat(dataset.getId(), is(datasetId));
    }

    @Test
    public void データセットにファイルを追加できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        client.addFiles(datasetId, new File("README.md"), new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        assertThat(files.getResults().size(), is(3));
    }

    @Test
    public void ファイルを更新できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.updateFile(datasetId, fileId, new File(".gitignore"));
        RangeSlice<DatasetFile> files2 = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileName = files2.getResults().get(0).getName();
        assertThat(fileName, is(".gitignore"));
    }

    @Test
    public void ファイル情報を更新できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        UpdateFileMetaParam param = new UpdateFileMetaParam("HOGE.md", "Hoge");
        client.updateFileMetaInfo(datasetId, fileId, param);
        Dataset dataset2 = client.getDataset(datasetId);
        RangeSlice<DatasetFile> files2 = client.getDatasetFiles(datasetId, new GetRangeParam());
        DatasetFile file = files2.getResults().get(0);
        assertThat(file.getName(), is("HOGE.md"));
        assertThat(file.getDescription(), is("Hoge"));
    }

    @Test
    public void ファイルを削除できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        String fileId = client.getDatasetFiles(datasetId, new GetRangeParam()).getResults().get(0).getId();
        client.deleteFile(datasetId, fileId);
        Dataset dataset2 = client.getDataset(datasetId);
        Optional<DatasetFile> file = client.getDatasetFiles(datasetId, new GetRangeParam()).getResults().stream().findFirst();
        assertThat(file, is(Optional.empty()));
    }

    @Test
    public void データセットの情報を変更できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
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

    @Test
    public void データセットの画像を追加できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        assertThat(client.getDataset(datasetId).getImages().size(), is(1));
        client.addImagesToDataset(datasetId, new File("test.png"));
        assertThat(client.getDataset(datasetId).getImages().size(), is(2));
    }

    @Test
    public void データセットの画像を変更できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        String primary = client.getDataset(datasetId).getPrimaryImage();
        DatasetAddImages images = client.addImagesToDataset(datasetId, new File("test.png"));
        Image image = images.getImages().stream().filter(x -> !x.getId().equals(primary)).findFirst().get();
        client.setPrimaryImageToDataset(datasetId, new SetPrimaryImageParam(image.getId()));
        Dataset dataset = client.getDataset(datasetId);
        assertThat(dataset.getPrimaryImage(), is(image.getId()));
    }

    @Test
    public void データセットのFeaturedDataset画像を変更できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        String featured = client.getDataset(datasetId).getFeaturedImage();
        DatasetAddImages images = client.addImagesToDataset(datasetId, new File("test.png"));
        Image image = images.getImages().stream().filter(x -> !x.getId().equals(featured)).findFirst().get();
        client.setFeaturedImageToDataset(datasetId, image.getId());
        Dataset dataset = client.getDataset(datasetId);
        assertThat(dataset.getFeaturedImage(), is(image.getId()));
    }

    @Test
    public void データセットの画像を削除できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        DatasetAddImages addImg = client.addImagesToDataset(datasetId, new File("test.png"));
        assertThat(client.getDataset(datasetId).getImages().size(), is(2));
        client.deleteImageToDataset(datasetId, addImg.getImages().get(0).getId());
        assertThat(client.getDataset(datasetId).getImages().size(), is(1));
    }

    @Test
    public void データセットの権限を変更できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        String userId = client.getDataset(datasetId).getOwnerShips().stream().findFirst().get().getId();
        SetAccessLevelParam param = new SetAccessLevelParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1, 3);
        client.changeAccessLevel(datasetId, Arrays.asList(param));
        assertThat(client.getDataset(datasetId).getOwnerShips().stream().filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81")).findFirst().get().getAccessLevel(), is(3));
    }

    @Test
    public void データセットのゲスト権限を変更できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        client.changeGuestAccessLevel(datasetId, new SetGuestAccessLevelParam(3));
        assertThat(client.getDataset(datasetId).getPermission(), is(3));
    }

    @Test
    public void ファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Path dir = Paths.get("temp");
        if (! dir.toFile().exists()) {
            Files.createDirectory(dir);
        }
        File file = client.downloadFile(datasetId, fileId, "temp");
        assertThat(file.getName(), is("README.md"));
        assertThat(file.exists(), is(true));
        file.delete();
        dir.toFile().delete();
    }

    @Test
    public void 拡張子なしのファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        File original = Files.createFile(Paths.get("hoge")).toFile();
        client.createDataset(true, false, new File("hoge"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Path dir = Paths.get("temp");
        if (! dir.toFile().exists()) {
            Files.createDirectory(dir);
        }
        File file = client.downloadFile(datasetId, fileId, "temp");
        assertThat(file.getName(), is("hoge"));
        assertThat(file.exists(), is(true));
        original.delete();
        file.delete();
        dir.toFile().delete();
    }

    @Test
    public void ファイル名にドットを含むファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        File original = Files.createFile(Paths.get("a.b.txt")).toFile();
        client.createDataset(true, false, new File("a.b.txt"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Path dir = Paths.get("temp");
        if (! dir.toFile().exists()) {
            Files.createDirectory(dir);
        }
        File file = client.downloadFile(datasetId, fileId, "temp");
        assertThat(file.getName(), is("a.b.txt"));
        assertThat(file.exists(), is(true));
        original.delete();
        file.delete();
        dir.toFile().delete();
    }

    @Test
    public void 同名ファイルが既に存在している場合にファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Path dir = Paths.get("temp");
        if (! dir.toFile().exists()) {
            Files.createDirectory(dir);
        }
        Files.createFile(dir.resolve("README.md")).toFile();
        File file = client.downloadFile(datasetId, fileId, "temp");
        assertThat(file.getName(), is("README (1).md"));
        assertThat(file.exists(), is(true));
        for (File f : dir.toFile().listFiles()) {
            f.delete();
        }
        dir.toFile().delete();
    }

    @Test
    public void 同名ファイルが既に存在している場合にファイルをダウンロードできるか2() throws IOException {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Path dir = Paths.get("temp");
        if (! dir.toFile().exists()) {
            Files.createDirectory(dir);
        }
        Files.createFile(dir.resolve("README.md")).toFile();
        Files.createFile(dir.resolve("README (1).md")).toFile();
        File file = client.downloadFile(datasetId, fileId, "temp");
        assertThat(file.getName(), is("README (2).md"));
        assertThat(file.exists(), is(true));
        for (File f : dir.toFile().listFiles()) {
            f.delete();
        }
        dir.toFile().delete();
    }

    @Test
    public void マルチバイトのファイル名のファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        File original = Files.createFile(Paths.get("あああああ.txt")).toFile();
        client.createDataset(true, false, new File("あああああ.txt"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Path dir = Paths.get("temp");
        if (! dir.toFile().exists()) {
            Files.createDirectory(dir);
        }
        File file = client.downloadFile(datasetId, fileId, "temp");
        assertThat(file.getName(), is("あああああ.txt"));
        assertThat(file.exists(), is(true));
        original.delete();
        file.delete();
        dir.toFile().delete();
    }

    @Test
    public void 保存先を変更できるか() {
        DsmoqClient client = create();
        client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        String datasetId = summaries.stream().findFirst().get().getId();
        client.changeDatasetStorage(datasetId, new ChangeStorageParam(true, true));
        Dataset dataset = client.getDataset(datasetId);
        assertThat(dataset.getLocalState(), is(1));
        assertThat(dataset.getS3State(), is(2));
    }

    @Test
    public void グループを検索できるか() {
        DsmoqClient client = create();
        List<GroupsSummary> summaries = client.getGroups(new GetGroupsParam()).getResults();
        assertThat(summaries.size(), is(0));
    }

    @Test
    public void グループを作成し削除できるか() {
        DsmoqClient client = create();
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

    @Test
    public void グループの詳細情報を取得できるか() {
        DsmoqClient client = create();
        client.createGroup(new CreateGroupParam("hoge1", "description"));
        List<GroupsSummary> summaries = client.getGroups(new GetGroupsParam()).getResults();
        String groupId = summaries.get(0).getId();
        Group group = client.getGroup(groupId);
        assertThat(group.getId(), is(groupId));
    }

    @Test
    public void グループのメンバーを取得できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge2", "description"));
        String groupId = group.getId();
        RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().size(), is(1));
        assertThat(members.getResults().get(0).getName(), is("dummy"));
    }

    @Test
    public void グループの情報を変更できるか() {
        DsmoqClient client = create();
        String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
        client.updateGroup(groupId, new UpdateGroupParam("hoge2", "description2"));
        Group group = client.getGroup(groupId);
        assertThat(group.getName(), is("hoge2"));
        assertThat(group.getDescription(), is("description2"));
    }

    @Test
    public void グループに画像を追加できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        client.addImagesToGroup(groupId, new File("test.png"));
        assertThat(client.getGroup(groupId).getImages().size(), is(2));
    }

    @Test
    public void グループのメイン画像を変更できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        GroupAddImages image = client.addImagesToGroup(groupId, new File("test.png"));
        String imageId = image.getImages().get(0).getId();
        client.setPrimaryImageToGroup(groupId, new SetPrimaryImageParam(imageId));
        assertThat(client.getGroup(groupId).getPrimaryImage(), is(imageId));
    }

    @Test
    public void グループの画像を削除できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        GroupAddImages image = client.addImagesToGroup(groupId, new File("test.png"));
        String imageId = image.getImages().get(0).getId();
        client.deleteImageToGroup(groupId, imageId);
        assertThat(client.getGroup(groupId).getImages().size(), is(1));
    }

    @Test
    public void グループにメンバを追加できるか() {
        DsmoqClient client = create();
        String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
        client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().size(), is(2));
    }

    @Test
    public void メンバのロールを変更できるか() {
        DsmoqClient client = create();
        String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
        client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        client.setMemberRole(groupId, "023bfa40-e897-4dad-96db-9fd3cf001e81", new SetMemberRoleParam(2));
        RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().stream().filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81")).findFirst().get().getRole(), is(2));
    }

    @Test
    public void メンバを削除できるか() {
        DsmoqClient client = create();
        String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
        client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        client.deleteMember(groupId, "023bfa40-e897-4dad-96db-9fd3cf001e81");
        RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().size(), is(1));
    }

    @Test
    public void プロフィールを取得できるか() {
        DsmoqClient client = create();
        User user = client.getProfile();
        assertThat(user.getName(), is("dummy"));
    }

    @Test
    public void プロフィールを変更できるか() {
        DsmoqClient client = create();
        User user = client.getProfile();
        client.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(), user.getTitle(), "test"));
        assertThat(client.getProfile().getDescription(), is("test"));
        client.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(), user.getTitle(), "description"));
    }

    @Test
    public void プロフィール画像を変更できるか() {
        DsmoqClient client = create();
        User user = client.getProfile();
        client.updateProfileIcon(new File("test.png"));
    }

    @Test
    public void メールアドレスを変更できるか() {
        DsmoqClient client = create();
        client.updateEmail(new UpdateEmailParam("test2@test.jp"));
        assertThat(client.getProfile().getMailAddress(), is("test2@test.jp"));
        client.updateEmail(new UpdateEmailParam("dummy@test.jp"));
    }

    @Test
    public void パスワードを変更できるか() {
        DsmoqClient client = create();
        client.changePassword(new ChangePasswordParam("password", "passw0rd"));
        client.changePassword(new ChangePasswordParam("passw0rd", "password"));
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
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        DatasetTask task = client.changeDatasetStorage(datasetId, new ChangeStorageParam(false, true));
        TaskStatus status = client.getTaskStatus(task.getTaskId());
        assertThat(status.getStatus(), is(0));
    }

    @Test
    public void データセットの画像一覧を取得できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        client.addImagesToDataset(datasetId, new File("test.png"));
        RangeSlice<DatasetGetImage> images = client.getDatasetImage(datasetId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(2));
    }

    @Test
    public void アクセス権限一覧を取得できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetOwnership> images = client.getAccessLevel(datasetId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(1));
    }

    @Test
    public void データセットをコピーできるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        String copiedDatasetId = client.copyDataset(datasetId);
        Dataset copiedDataset = client.getDataset(copiedDatasetId);
        assertThat(copiedDataset.getId(), is(copiedDatasetId));
    }

    @Test
    public void AttributeをImportできるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        client.importAttribute(datasetId, new File("test.csv"));
        Dataset dataset2 = client.getDataset(datasetId);
        assertThat(dataset2.getMeta().getAttributes().size(), is(2));
    }

    @Test
    public void AttributeをExportできるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        client.importAttribute(datasetId, new File("test.csv"));
        File file = client.exportAttribute(datasetId, ".");
        assertThat(file.exists(), is(true));
        file.delete();
    }

    @Test
    public void グループの画像一覧を取得できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        client.addImagesToGroup(groupId, new File("test.png"));
        RangeSlice<GroupGetImage> images = client.getGroupImage(groupId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(2));
    }

    @Test
    public void 統計情報を取得できるか() {
        DsmoqClient client = create();
        List<StatisticsDetail> stats = client.getStatistics(new StatisticsParam(Optional.of(new DateTime()), Optional.of(new DateTime())));
        assertThat(stats.size(), is(0));
    }

    @Test
    public void canGetFiles() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        assertThat(files.getSummary().getTotal(), is(1));
    }

    @Test
    public void canGetZippedFiles() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("test.zip"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        RangeSlice<DatasetZipedFile> zippedFiles = client.getDatasetZippedFiles(datasetId, files.getResults().get(0).getId(), new GetRangeParam());
        assertThat(zippedFiles.getSummary().getTotal(), is(1));
    }

    public static DsmoqClient create() {
        return DsmoqClient.create("http://localhost:8080", "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba", "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
    }
}

