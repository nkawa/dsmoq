
import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.*;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;
import jp.ac.nagoya_u.dsmoq.sdk.util.*;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.Is.*;
import static org.hamcrest.core.IsInstanceOf.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SDKDownloadFileTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

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
    public void downloadFileのdatasetIdがnullの場合NullPointerExceptionが発生() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        client.downloadFile(null, "", content -> "");
    }

    @Test
    public void downloadFileのfileIdがnullの場合NullPointerExceptionが発生() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        client.downloadFile("", null, content -> "");
    }

    @Test
    public void downloadFileのfがnullの場合NullPointerExceptionが発生() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        client.downloadFile("", "", null);
    }

    @Test
    public void downloadFileでサーバに接続できない場合例外が発生() {
        thrown.expect(DsmoqHttpException.class);
        thrown.expectCause(instanceOf(HttpHostConnectException.class));
        DsmoqClient client = DsmoqClient.create("http://localhost:8081", "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649", "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
        client.downloadFile("", "", content -> null);
    }

    @Test
    public void downloadFileでdatasetIdが空文字列の場合例外が発生() {
        thrown.expect(DsmoqHttpException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.downloadFile("", fileId, content -> null);
    }

    @Test
    public void downloadFileでfileIdが空文字列の場合例外が発生() {
        thrown.expect(DsmoqHttpException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        client.downloadFile(dataset.getId(), "", content -> null);
    }

    @Test
    public void downloadFileでdatasetIdで指定した対象が存在しない場合例外が発生() {
        thrown.expect(DsmoqHttpException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.downloadFile("023bfa40-e897-4dad-96db-9fd3cf001e79", fileId, content -> null);
    }

    @Test
    public void downloadFileでfileIdで指定した対象が存在しない場合例外が発生() {
        thrown.expect(DsmoqHttpException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        client.downloadFile(dataset.getId(), "023bfa40-e897-4dad-96db-9fd3cf001e79", content -> null);
    }

    @Test
    public void downloadFileで権限のないのないファイルを指定すると例外が発生() {
        thrown.expect(DsmoqHttpException.class);
        thrown.expectCause(instanceOf(ErrorRespondedException.class));
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File("README.md"));
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        DsmoqClient client2 = DsmoqClient.create("http://localhost:8080", "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649", "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
        client2.downloadFile(dataset.getId(), fileId, content -> null);
    }

    @Test
    public void downloadFile_サーバローカルのZIP以外のファイルをDLできる() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.png");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        RangeSlice<DatasetFile> files = client.getDatasetFiles(dataset.getId(), new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(dataset.getId(), fileId, content -> {
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
    public void downloadFile_サーバローカルのZIPファイルをDLできる() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.zip");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, fileId, content -> {
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
    public void downloadFile_サーバローカルのZIP内ファイルをDLできる() throws IOException {
        DsmoqClient client = create();
        Path original = Paths.get("testdata", "test.zip");
        Dataset dataset = client.createDataset(true, false, original.toFile());
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        RangeSlice<DatasetZipedFile> zFiles = client.getDatasetZippedFiles(datasetId, fileId, new GetRangeParam());
        String zFileId = zFiles.getResults().get(0).getId();
        byte[] downloaded = client.downloadFile(datasetId, zFileId, content -> {
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
    public void downloadFile_ascii文字ファイル名を扱える() throws IOException {
        checkFileName("hoge");
    }

    @Test
    public void downloadFile_マルチバイト文字ファイル名を扱える() throws IOException {
        checkFileName("表予申能十ソ");
    }

    @Test
    public void downloadFile_asciiマルチバイト混在ファイル名を扱える() throws IOException {
        checkFileName("README表予申能十ソ.md");
    }

    private void checkFileName(String originalFileName) throws IOException {
        File original = Files.createFile(Paths.get(originalFileName)).toFile();
        DsmoqClient client = create();
        Dataset dataset = client.createDataset(true, false, new File(originalFileName));
        original.delete();
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String fileName = client.downloadFile(datasetId, fileId, content -> content.getName());
        assertThat(fileName, is(originalFileName));
    }

    public static DsmoqClient create() {
        return DsmoqClient.create("http://localhost:8080", "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba", "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
    }
}
