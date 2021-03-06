
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.http.conn.HttpHostConnectException;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.AddMemberParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.ChangePasswordParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.ChangeStorageParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.CreateGroupParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetDatasetsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetGroupsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetMembersParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetAccessLevelParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetGuestAccessLevelParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetMemberRoleParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetPrimaryImageParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.StatisticsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateDatasetAttributeParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateDatasetMetaParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateEmailParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateFileMetaParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateGroupParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateProfileParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.Dataset;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetAddImages;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetAttribute;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetGetImage;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetMetaData;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetOwnership;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetTask;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetZipedFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetsSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.Group;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupAddImages;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupGetImage;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupsSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.Image;
import jp.ac.nagoya_u.dsmoq.sdk.response.MemberSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.RangeSlice;
import jp.ac.nagoya_u.dsmoq.sdk.response.StatisticsDetail;
import jp.ac.nagoya_u.dsmoq.sdk.response.TaskStatus;
import jp.ac.nagoya_u.dsmoq.sdk.response.User;
import jp.ac.nagoya_u.dsmoq.sdk.util.ConnectionLostException;
import jp.ac.nagoya_u.dsmoq.sdk.util.HttpStatusException;

public class SDKTest {
    public static DsmoqClient create() {
        return DsmoqClient.create("http://localhost:8080",
                "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba",
                "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void AttributeをExportできるか() throws IOException {
        DsmoqClient client = create();
        Path source = Paths.get("testdata", "test.csv");
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        client.importAttribute(datasetId, source.toFile());
        String data = client.exportAttribute(datasetId, content -> {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return new String(bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        // 順番保障はしていないため、登録したattributeを含むかどうかで判断
        assertThat(data.replaceAll("\r\n", "\n"), is("aaaaa,1\n"));
        assertThat(data.replaceAll("\r\n", "\n"), is("fuga,2\n"));
    }

    @Test
    public void AttributeをImportできるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        client.importAttribute(datasetId, new File("testdata/test.csv"));
        Dataset dataset2 = client.getDataset(datasetId);
        assertThat(dataset2.getMeta().getAttributes().size(), is(2));
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
        Dataset dataset = client.createDataset(true, false, new File("testdata/test.zip"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        RangeSlice<DatasetZipedFile> zippedFiles = client.getDatasetZippedFiles(datasetId,
                files.getResults().get(0).getId(), new GetRangeParam());
        assertThat(zippedFiles.getSummary().getTotal(), is(1));
    }

    @Test
    public void HEADリクエスト_LocalZIP内ファイル() throws ZipException, IOException {
        DsmoqClient client = create();
        File file = new File("testdata/test.zip");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        RangeSlice<DatasetZipedFile> zFiles = client.getDatasetZippedFiles(datasetId, fileId, new GetRangeParam());
        String zFileId = zFiles.getResults().get(0).getId();
        ZipFile zf = new ZipFile(file);
        ZipEntry zEntry = zf.entries().nextElement();
        Long contentLength = client.getFileSize(datasetId, zFileId);
        assertThat(contentLength, is(zEntry.getSize()));
    }

    @Test
    public void HEADリクエスト_Local通常ファイル() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Long contentLength = client.getFileSize(datasetId, fileId);
        assertThat(contentLength, is(file.length()));
    }

    @After
    public void tearDown() {
        DsmoqClient client = create();
        List<GroupsSummary> groups = client.getGroups(new GetGroupsParam(Optional.empty(),
                Optional.of("023bfa40-e897-4dad-96db-9fd3cf001e79"), Optional.empty(), Optional.empty())).getResults();
        for (GroupsSummary group : groups) {
            try {
                client.deleteGroup(group.getId());
            } catch (Exception e) {
                // do nothing
            }
        }
        GetDatasetsConditionParam param = new GetDatasetsConditionParam();
        param.add(new QueryContainCondition(""));
        List<DatasetsSummary> datasets = client.getDatasets(param).getResults();
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
    public void アカウント一覧を取得できるか() {
        DsmoqClient client = create();
        List<User> users = client.getAccounts();
        assertThat(users.size(), is(2));
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
    public void グループにメンバを追加できるか() {
        DsmoqClient client = create();
        String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
        client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().size(), is(2));
    }

    @Test
    public void グループに画像を追加できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        client.addImagesToGroup(groupId, new File("testdata/test.png"));
        assertThat(client.getGroup(groupId).getImages().size(), is(2));
    }

    @Test
    public void グループのメイン画像を変更できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        GroupAddImages image = client.addImagesToGroup(groupId, new File("testdata/test.png"));
        String imageId = image.getImages().get(0).getId();
        client.setPrimaryImageToGroup(groupId, new SetPrimaryImageParam(imageId));
        assertThat(client.getGroup(groupId).getPrimaryImage(), is(imageId));
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
    public void グループの画像を削除できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        GroupAddImages image = client.addImagesToGroup(groupId, new File("testdata/test.png"));
        String imageId = image.getImages().get(0).getId();
        client.deleteImageToGroup(groupId, imageId);
        assertThat(client.getGroup(groupId).getImages().size(), is(1));
    }

    @Test
    public void グループの画像一覧を取得できるか() {
        DsmoqClient client = create();
        Group group = client.createGroup(new CreateGroupParam("hoge", "description"));
        String groupId = group.getId();
        client.addImagesToGroup(groupId, new File("testdata/test.png"));
        RangeSlice<GroupGetImage> images = client.getGroupImage(groupId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(2));
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
    public void グループの情報を変更できるか() {
        DsmoqClient client = create();
        String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
        client.updateGroup(groupId, new UpdateGroupParam("hoge2", "description2"));
        Group group = client.getGroup(groupId);
        assertThat(group.getName(), is("hoge2"));
        assertThat(group.getDescription(), is("description2"));
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
    public void サーバに接続できない場合例外が発生() {
        thrown.expect(ConnectionLostException.class);
        thrown.expectCause(instanceOf(HttpHostConnectException.class));
        DsmoqClient client = DsmoqClient.create("http://localhost:8081",
                "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649",
                "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
        client.createDataset("hello", true, false);
    }

    @Test
    public void ダウンロードしたファイルの中身が壊れていないか_ASCII() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.csv");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, fileId, content -> {
            assertThat(content.getName(), is("test.csv"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assert.assertArrayEquals(downloaded, Files.readAllBytes(original));
    }

    @Test
    public void ダウンロードしたファイルの中身が壊れていないか_バイナリ() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.png");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, fileId, content -> {
            assertThat(content.getName(), is("test.png"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assert.assertArrayEquals(downloaded, Files.readAllBytes(original));
    }

    @Test
    public void ダウンロードしたファイルの中身が壊れていないか_マルチバイト_EUC() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "multibyte_euc.txt");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, fileId, content -> {
            assertThat(content.getName(), is("multibyte_euc.txt"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assert.assertArrayEquals(downloaded, Files.readAllBytes(original));
    }

    @Test
    public void ダウンロードしたファイルの中身が壊れていないか_マルチバイト_SJIS() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "multibyte_sjis.txt");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, fileId, content -> {
            assertThat(content.getName(), is("multibyte_sjis.txt"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assert.assertArrayEquals(downloaded, Files.readAllBytes(original));
    }

    @Test
    public void ダウンロードしたファイルの中身が壊れていないか_マルチバイト_UTF8_BOM() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "multibyte_utf8_bom.txt");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, fileId, content -> {
            assertThat(content.getName(), is("multibyte_utf8_bom.txt"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assert.assertArrayEquals(downloaded, Files.readAllBytes(original));
    }

    @Test
    public void ダウンロードしたファイルの中身が壊れていないか_マルチバイト_UTF8_NoBOM() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "multibyte_utf8_nobom.txt");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, fileId, content -> {
            assertThat(content.getName(), is("multibyte_utf8_nobom.txt"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assert.assertArrayEquals(downloaded, Files.readAllBytes(original));
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
    public void データセットにファイルを追加できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        client.addFiles(datasetId, new File("README.md"), new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        assertThat(files.getResults().size(), is(3));
    }

    @Test
    public void データセットのFeaturedDataset画像を変更できるか() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        String featured = client.getDataset(datasetId).getFeaturedImage();
        DatasetAddImages images = client.addImagesToDataset(datasetId, new File("testdata/test.png"));
        Image image = images.getImages().stream().filter(x -> !x.getId().equals(featured)).findFirst().get();
        client.setFeaturedImageToDataset(datasetId, image.getId());
        Dataset updated = client.getDataset(datasetId);
        assertThat(updated.getFeaturedImage(), is(image.getId()));
    }

    @Test
    public void データセットのゲスト権限を変更できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        assertThat(dataset.getDefaultAccessLevel(), is(0));
        client.changeGuestAccessLevel(datasetId, new SetGuestAccessLevelParam(2));
        assertThat(client.getDataset(datasetId).getDefaultAccessLevel(), is(2));
    }

    @Test
    public void データセットの画像を削除できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        DatasetAddImages addImg = client.addImagesToDataset(datasetId, new File("testdata/test.png"));
        assertThat(client.getDataset(datasetId).getImages().size(), is(2));
        client.deleteImageToDataset(datasetId, addImg.getImages().get(0).getId());
        assertThat(client.getDataset(datasetId).getImages().size(), is(1));
    }

    @Test
    public void データセットの画像を追加できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        assertThat(client.getDataset(datasetId).getImages().size(), is(1));
        client.addImagesToDataset(datasetId, new File("testdata/test.png"));
        assertThat(client.getDataset(datasetId).getImages().size(), is(2));
    }

    @Test
    public void データセットの画像を変更できるか() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        String primary = client.getDataset(datasetId).getPrimaryImage();
        DatasetAddImages images = client.addImagesToDataset(datasetId, new File("testdata/test.png"));
        Image image = images.getImages().stream().filter(x -> !x.getId().equals(primary)).findFirst().get();
        client.setPrimaryImageToDataset(datasetId, new SetPrimaryImageParam(image.getId()));
        Dataset updated = client.getDataset(datasetId);
        assertThat(updated.getPrimaryImage(), is(image.getId()));
    }

    @Test
    public void データセットの画像一覧を取得できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        client.addImagesToDataset(datasetId, new File("testdata/test.png"));
        RangeSlice<DatasetGetImage> images = client.getDatasetImage(datasetId, new GetRangeParam());
        assertThat(images.getSummary().getTotal(), is(2));
    }

    @Test
    public void データセットの権限を変更できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        String userId = client.getDataset(datasetId).getOwnerShips().stream().findFirst().get().getId();
        SetAccessLevelParam param = new SetAccessLevelParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1, 3);
        client.changeAccessLevel(datasetId, Arrays.asList(param));
        assertThat(client.getDataset(datasetId).getOwnerShips().stream()
                .filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81")).findFirst().get()
                .getAccessLevel(), is(3));
    }

    @Test
    public void データセットの情報を変更できるか() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
        param.setName("hoge");
        param.setDescription("dummy description");
        param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
        param.setAttributes(Arrays.asList(
                new UpdateDatasetAttributeParam("attr1", "value1"),
                new UpdateDatasetAttributeParam("attr2", "value2"),
                new UpdateDatasetAttributeParam("tag", "$tag")
        ));
        client.updateDatasetMetaInfo(datasetId, param);

        Dataset updated = client.getDataset(datasetId);
        DatasetMetaData metadata = updated.getMeta();
        assertThat(metadata.getName(), is("hoge"));
        assertThat(metadata.getDescription(), is("dummy description"));
        List<DatasetAttribute> attr = metadata.getAttributes();
        assertThat(attr.size(), is(3));
        assertThat(attr.get(0).getName(), is("tag"));
        assertThat(attr.get(0).getValue(), is("$tag"));
        assertThat(attr.get(1).getName(), is("attr2"));
        assertThat(attr.get(1).getValue(), is("value2"));
        assertThat(attr.get(2).getName(), is("attr1"));
        assertThat(attr.get(2).getValue(), is("value1"));
    }

    @Test
    public void 不正な属性指定でエラーとなるか_keyなし_valueなし() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
        param.setName("hoge");
        param.setDescription("dummy description");
        param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
        param.setAttributes(Collections.singletonList(new UpdateDatasetAttributeParam("", "")));

        try {
            client.updateDatasetMetaInfo(datasetId, param);
            fail();
        } catch (HttpStatusException e) {
            assertTrue(true);
        }
    }

    @Test
    public void 不正な属性指定でエラーとなるか_keyなし_valueあり() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
        param.setName("hoge");
        param.setDescription("dummy description");
        param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
        param.setAttributes(Collections.singletonList(new UpdateDatasetAttributeParam("", "value1")));

        try {
            client.updateDatasetMetaInfo(datasetId, param);
            fail();
        } catch (HttpStatusException e) {
            assertTrue(true);
        }
    }

    @Test
    public void 不正な属性指定でエラーとなるか_keyあり_valueなし() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
        param.setName("hoge");
        param.setDescription("dummy description");
        param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
        param.setAttributes(Collections.singletonList(new UpdateDatasetAttributeParam("attr1", "")));

        try {
            client.updateDatasetMetaInfo(datasetId, param);
            fail();
        } catch (HttpStatusException e) {
            assertTrue(true);
        }
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
    public void データセットを一意に特定できるか() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        Dataset got = client.getDataset(created.getId());
        assertThat(got.getId(), is(created.getId()));
    }

    @Test
    public void データセットを作成して削除できるか() {
        DsmoqClient client = create();
        assertThat(client.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
        assertThat(summaries.size(), is(1));
        String datasetId = dataset.getId();
        client.deleteDataset(datasetId);
        assertThat(client.getDatasets(new GetDatasetsParam()).getResults().size(), is(0));
    }

    @Test
    public void パスワードを変更できるか() {
        DsmoqClient client = create();
        client.changePassword(new ChangePasswordParam("password", "passw0rd"));
        client.changePassword(new ChangePasswordParam("passw0rd", "password"));
    }

    @Test
    public void ファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String fileName = client.downloadFile(datasetId, fileId, content -> content.getName());
        assertThat(fileName, is("README.md"));
    }

    @Test
    public void ファイルを更新できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.updateFile(datasetId, fileId, new File(".gitignore"));
        RangeSlice<DatasetFile> files2 = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileName = files2.getResults().get(0).getName();
        assertThat(fileName, is(".gitignore"));
    }

    @Test
    public void ファイルを削除できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
        String fileId = client.getDatasetFiles(datasetId, new GetRangeParam()).getResults().get(0).getId();
        client.deleteFile(datasetId, fileId);
        Dataset dataset2 = client.getDataset(datasetId);
        Optional<DatasetFile> file = client.getDatasetFiles(datasetId, new GetRangeParam()).getResults().stream()
                .findFirst();
        assertThat(file, is(Optional.empty()));
    }

    @Test
    public void ファイルを部分的にダウンロードしInputStreamで取得できるか() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "abc.txt");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String data = client.downloadFileWithRange(datasetId, fileId, 9L, 14L, content -> {
            assertThat(content.getName(), is("abc.txt"));
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(content.getContent()));
                return in.readLine();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(data, is("jklmn"));
    }

    @Test
    public void ファイルを部分的にダウンロードしOutputStreamで出力できるか() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "abc.txt");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String data = client.downloadFileWithRange(datasetId, fileId, 9L, 14L, content -> {
            assertThat(content.getName(), is("abc.txt"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(data, is("jklmn"));
    }

    @Test
    public void ファイル情報を更新できるか() {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        String datasetId = dataset.getId();
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
    public void ファイル名にドットを含むファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("testdata/a.b.txt"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String fileName = client.downloadFile(datasetId, fileId, content -> content.getName());
        assertThat(fileName, is("a.b.txt"));
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
        client.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(),
                user.getTitle(), "test"));
        assertThat(client.getProfile().getDescription(), is("test"));
        client.updateProfile(new UpdateProfileParam(user.getName(), user.getFullname(), user.getOrganization(),
                user.getTitle(), "description"));
    }

    @Test
    public void プロフィール画像を変更できるか() {
        DsmoqClient client = create();
        User user = client.getProfile();
        client.updateProfileIcon(new File("testdata/test.png"));
    }

    @Test
    public void マルチバイトのファイル名のファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("testdata/表予申能十ソ.txt"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String fileName = client.downloadFile(datasetId, fileId, content -> content.getName());
        assertThat(fileName, is("表予申能十ソ.txt"));
    }

    @Test
    public void マルチバイト文字を含むResponseを取り扱えるか() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
        param.setName("ほげ");
        param.setDescription("日本語の説明");
        param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
        param.setAttributes(Arrays.asList(
                new UpdateDatasetAttributeParam("属性１", "値１"),
                new UpdateDatasetAttributeParam("属性２", "値２"),
                new UpdateDatasetAttributeParam("タグ", "$tag")
        ));

        client.updateDatasetMetaInfo(datasetId, param);
        Dataset updated = client.getDataset(datasetId);
        DatasetMetaData metadata = updated.getMeta();
        assertThat(metadata.getName(), is("ほげ"));
        assertThat(metadata.getDescription(), is("日本語の説明"));
        List<DatasetAttribute> attr = metadata.getAttributes();
        assertThat(attr.size(), is(3));
        assertThat(attr.get(0).getName(), is("タグ"));
        assertThat(attr.get(0).getValue(), is("$tag"));
        assertThat(attr.get(1).getName(), is("属性２"));
        assertThat(attr.get(1).getValue(), is("値２"));
        assertThat(attr.get(2).getName(), is("属性１"));
        assertThat(attr.get(2).getValue(), is("値１"));
    }

    @Test
    public void メールアドレスを変更できるか() {
        DsmoqClient client = create();
        client.updateEmail(new UpdateEmailParam("test2@test.jp"));
        assertThat(client.getProfile().getMailAddress(), is("test2@test.jp"));
        client.updateEmail(new UpdateEmailParam("dummy@test.jp"));
    }

    @Test
    public void メンバのロールを変更できるか() {
        DsmoqClient client = create();
        String groupId = client.createGroup(new CreateGroupParam("hoge", "description")).getId();
        client.addMember(groupId, Arrays.asList(new AddMemberParam("023bfa40-e897-4dad-96db-9fd3cf001e81", 1)));
        client.setMemberRole(groupId, "023bfa40-e897-4dad-96db-9fd3cf001e81", new SetMemberRoleParam(2));
        RangeSlice<MemberSummary> members = client.getMembers(groupId, new GetMembersParam());
        assertThat(members.getResults().stream().filter(x -> x.getId().equals("023bfa40-e897-4dad-96db-9fd3cf001e81"))
                .findFirst().get().getRole(), is(2));
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
    public void 拡張子なしのファイルをダウンロードできるか() throws IOException {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("testdata/hoge"));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String fileName = client.downloadFile(datasetId, fileId, content -> content.getName());
        assertThat(fileName, is("hoge"));
    }

    @Test
    public void 権限のないのないデータセットを取得すると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(403, "AccessDenied"));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset("hello", true, false);
        DsmoqClient client2 = DsmoqClient.create("http://localhost:8080",
                "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649",
                "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
        client2.getDataset(dataset.getId());
    }

    @Test
    public void ID形式が不正なデータセットを取得すると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(404, "Illegal Argument"));
        DsmoqClient client = create();
        Dataset dataset = client.getDataset("hello");
    }

    @Test
    public void 存在しないデータセットを取得すると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(404, "NotFound"));
        DsmoqClient client = create();
        Dataset dataset = client.getDataset("1050f556-7fee-4032-81e7-326e5f1b82fb");
    }

    @Test
    public void 統計情報を取得できるか() {
        DsmoqClient client = create();
        List<StatisticsDetail> stats = client
                .getStatistics(new StatisticsParam(Optional.of(new DateTime()), Optional.of(new DateTime())));
        assertThat(stats.size(), is(0));
    }

    @Test
    public void 不正なデータセットを作成しようとすると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(400, "Illegal Argument"));
        DsmoqClient client = create();
        client.createDataset(false, false, new File("README.md"));
    }

    @Test
    public void 保存先を変更できるか() {
        DsmoqClient client = create();
        Dataset created = client.createDataset(true, false, new File("README.md"));
        String datasetId = created.getId();
        client.changeDatasetStorage(datasetId, new ChangeStorageParam(true, true));
        Dataset updated = client.getDataset(datasetId);
        assertThat(updated.getLocalState(), is(1));
        assertThat(updated.getS3State(), is(2));
    }

    @Test
    public void 不正なキーでデータセットを作成しようとすると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(403, "Unauthorized"));
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "hello", "world");
        client.createDataset(true, false, new File("README.md"));
    }

    @Test
    public void データセットから唯一のオーナーの権限をなくすと例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(400, "BadRequest"));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset("hello", true, false);
        client.changeAccessLevel(dataset.getId(), Arrays.asList(new SetAccessLevelParam("023bfa40-e897-4dad-96db-9fd3cf001e79", 1, 0)));
    }

    @Test
    public void データセットの権限設定で不正パラメタ不正値IDを入れると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(400, "Illegal Argument"));
        DsmoqClient client = create();
        client.changeAccessLevel("hello", Arrays.asList(new SetAccessLevelParam("world", 1, 0)));
    }

    @Test
    public void 同名のグループを作成すると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(400, "BadRequest"));
        DsmoqClient client = create();
        client.createGroup(CreateGroupParam.apply("hello", ""));
        client.createGroup(CreateGroupParam.apply("hello", ""));
    }

    @Test
    public void リソースなし不整合で例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(404, "NotFound"));
        DsmoqClient client = create();
        client.createGroup(CreateGroupParam.apply("hello", ""));
        client.updateGroup("023bfa40-e897-4dad-96db-9fd3cf001e79", UpdateGroupParam.apply("hello", ""));
    }

    @Test
    public void 不正キー不整合で例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(403, "Unauthorized"));
        DsmoqClient client = create();
        client.createGroup(CreateGroupParam.apply("hello", ""));
        DsmoqClient client2 = DsmoqClient.create("http://localhost:8080", "hello", "world");
        client2.createGroup(CreateGroupParam.apply("hello", ""));
    }

    @Test
    public void 不正キーリソースなし不整合で例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(403, "Unauthorized"));
        DsmoqClient client = create();
        client.createGroup(CreateGroupParam.apply("hello", ""));
        DsmoqClient client2 = DsmoqClient.create("http://localhost:8080", "hello", "world");
        client2.updateGroup("023bfa40-e897-4dad-96db-9fd3cf001e79", UpdateGroupParam.apply("hello", ""));
    }

    @Test
    public void 不正キー権限なしで例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(403, "Unauthorized"));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset("hello", true, false);
        DsmoqClient client2 = DsmoqClient.create("http://localhost:8080", "hello", "world");
        client2.getDataset(dataset.getId());
    }
}
