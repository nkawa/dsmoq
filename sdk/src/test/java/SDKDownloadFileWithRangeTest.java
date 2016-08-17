
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.apache.http.conn.HttpHostConnectException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetDatasetsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetGroupsParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.Dataset;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetZipedFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetsSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.GroupsSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.RangeSlice;
import jp.ac.nagoya_u.dsmoq.sdk.util.ConnectionLostException;
import jp.ac.nagoya_u.dsmoq.sdk.util.ErrorRespondedException;
import jp.ac.nagoya_u.dsmoq.sdk.util.HttpStatusException;

public class SDKDownloadFileWithRangeTest {
    public static DsmoqClient create() {
        return DsmoqClient.create("http://localhost:8080",
                "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba",
                "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void downloadFileWithRange_asciiマルチバイト混在ファイル名を扱える() throws IOException {
        checkFileName("README表予申能十ソ.md");
    }

    @Test
    public void downloadFileWithRange_ascii文字ファイル名を扱える() throws IOException {
        checkFileName("hoge");
    }

    @Test
    public void downloadFileWithRange_サーバローカルのZIPファイルをDLできる() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.zip");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFileWithRange(datasetId, fileId, null, null, content -> {
            assertThat(content.getName(), is("test.zip"));
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
    public void downloadFileWithRange_サーバローカルのZIP以外のファイルをDLできる() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.png");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFileWithRange(dataset.getId(), fileId, null, null, content -> {
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
    public void downloadFileWithRange_サーバローカルのZIP内ファイルをDLできる() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.zip");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        RangeSlice<DatasetZipedFile> zFiles = client.getDatasetZippedFiles(datasetId, fileId, new GetRangeParam());
        String zFileId = zFiles.getResults().get(0).getId();
        byte[] downloaded = client.downloadFileWithRange(datasetId, zFileId, null, null, content -> {
            assertThat(content.getName(), is("test.png"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Assert.assertArrayEquals(downloaded, Files.readAllBytes(Paths.get("testdata", "test.png")));
    }

    @Test
    public void downloadFileWithRange_サイズ0のファイルをDLできる() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "empty.zip");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        RangeSlice<DatasetZipedFile> zFiles = client.getDatasetZippedFiles(datasetId, fileId, new GetRangeParam());
        String zFileId = zFiles.getResults().get(0).getId();
        byte[] downloaded = client.downloadFileWithRange(datasetId, zFileId, null, null, content -> {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(downloaded.length, is(0));
    }

    @Test
    public void downloadFileWithRange_サイズ1_from_0_to_1で1byteDLできる() throws IOException {
        byte[] downloaded = downloadByteTestData(1, 0L, 1L);
        Assert.assertArrayEquals(downloaded, "a".getBytes());
    }

    @Test
    public void downloadFileWithRange_サイズ1_from_0_to_nullで1byteDLできる() throws IOException {
        byte[] downloaded = downloadByteTestData(1, 0L, null);
        Assert.assertArrayEquals(downloaded, "a".getBytes());
    }

    @Test
    public void downloadFileWithRange_サイズ1_from_null_to_0で例外が発生() throws IOException {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        downloadByteTestData(1, null, 0L);
    }

    @Test
    public void downloadFileWithRange_サイズ1のファイルをDLできる() throws IOException {
        byte[] downloaded = downloadByteTestData(1, null, null);
        Assert.assertArrayEquals(downloaded, "a".getBytes());
    }

    @Test
    public void downloadFileWithRange_サイズ2_from_0_to_1で1byteDLできる() throws IOException {
        byte[] downloaded = downloadByteTestData(2, 0L, 1L);
        Assert.assertArrayEquals(downloaded, "a".getBytes());
    }

    @Test
    public void downloadFileWithRange_サイズ2_from_0_to_2で2byteDLできる() throws IOException {
        byte[] downloaded = downloadByteTestData(2, 0L, 2L);
        Assert.assertArrayEquals(downloaded, "ab".getBytes());
    }

    @Test
    public void downloadFileWithRange_サイズ2_from_0_to_3で例外が発生() throws IOException {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        downloadByteTestData(2, 0L, 3L);
    }

    @Test
    public void downloadFileWithRange_サイズ2_from_1_to_0で例外が発生() throws IOException {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        downloadByteTestData(2, 1L, 0L);
    }

    @Test
    public void downloadFileWithRange_サイズ2_from_1_to_2で後1byteDLできる() throws IOException {
        byte[] downloaded = downloadByteTestData(2, 1L, 2L);
        Assert.assertArrayEquals(downloaded, "b".getBytes());
    }

    @Test
    public void downloadFileWithRange_サイズ2_from_3_to_3で例外が発生() throws IOException {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        downloadByteTestData(2, 3L, 3L);
    }

    @Test
    public void downloadFileWithRange_マルチバイト文字ファイル名を扱える() throws IOException {
        checkFileName("表予申能十ソ");
    }

    @Test
    public void downloadFileWithRangeでdatasetIdが空文字列の場合例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.downloadFileWithRange("", fileId, null, null, content -> null);
    }

    @Test
    public void downloadFileWithRangeでdatasetIdで指定した対象が存在しない場合例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.downloadFileWithRange("023bfa40-e897-4dad-96db-9fd3cf001e79", fileId, null, null, content -> null);
    }

    @Test
    public void downloadFileWithRangeでfileIdが空文字列の場合例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        client.downloadFileWithRange(dataset.getId(), "", null, null, content -> null);
    }

    @Test
    public void downloadFileWithRangeでfileIdで指定した対象が存在しない場合例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        client.downloadFileWithRange(dataset.getId(), "023bfa40-e897-4dad-96db-9fd3cf001e79", null, null,
                content -> null);
    }

    @Test
    public void downloadFileWithRangeでサーバに接続できない場合例外が発生() {
        thrown.expect(ConnectionLostException.class);
        thrown.expectCause(instanceOf(HttpHostConnectException.class));
        DsmoqClient client = DsmoqClient.create("http://localhost:8081",
                "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649",
                "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
        client.downloadFileWithRange("", "", null, null, content -> null);
    }

    @Test
    public void downloadFileWithRangeで権限のないのないファイルを指定すると例外が発生() {
        thrown.expect(HttpStatusException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        DsmoqClient client2 = DsmoqClient.create("http://localhost:8080",
                "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649",
                "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
        client2.downloadFileWithRange(dataset.getId(), fileId, null, null, content -> null);
    }

    @Test
    public void downloadFileWithRangeのdatasetIdがnullの場合NullPointerExceptionが発生() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        client.downloadFileWithRange(null, "", null, null, content -> "");
    }

    @Test
    public void downloadFileWithRangeのfileIdがnullの場合NullPointerExceptionが発生() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        client.downloadFileWithRange("", null, null, null, content -> "");
    }

    @Test
    public void downloadFileWithRangeのfromが負の場合IllegalArgumentExceptionが発生() {
        thrown.expect(IllegalArgumentException.class);
        DsmoqClient client = create();
        client.downloadFileWithRange("", "", -1L, null, content -> "");
    }

    @Test
    public void downloadFileWithRangeのfがnullの場合NullPointerExceptionが発生() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        client.downloadFileWithRange("", "", null, null, null);
    }

    @Test
    public void downloadFileWithRangeのtoが負の場合IllegalArgumentExceptionが発生() {
        thrown.expect(IllegalArgumentException.class);
        DsmoqClient client = create();
        client.downloadFileWithRange("", "", null, -1L, content -> "");
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

    private void checkFileName(String originalFileName) throws IOException {
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("testdata/" + originalFileName));
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String fileName = client.downloadFileWithRange(datasetId, fileId, null, null, content -> content.getName());
        assertThat(fileName, is(originalFileName));
    }

    private byte[] downloadByteTestData(int size, Long from, Long to) throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", size + ".txt");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        return client.downloadFileWithRange(datasetId, fileId, from, to, content -> {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                content.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
