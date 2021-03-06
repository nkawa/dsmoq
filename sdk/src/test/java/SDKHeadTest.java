
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.junit.After;
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
import jp.ac.nagoya_u.dsmoq.sdk.util.HttpStatusException;

public class SDKHeadTest {
    public static DsmoqClient create() {
        return DsmoqClient.create("http://localhost:8080",
                "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba",
                "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
    }

    public static DsmoqClient createDummyUserClient() {
        return DsmoqClient.create("http://localhost:8080",
                "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649",
                "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void getFileSize_nullチェック1() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.getFileSize(null, fileId);
    }

    @Test
    public void getFileSize_nullチェック2() {
        thrown.expect(NullPointerException.class);
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        client.getFileSize(datasetId, null);
    }

    @Test
    public void getFileSize_ZIP() {
        DsmoqClient client = create();
        File file = new File("testdata/test.zip");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Long size = client.getFileSize(datasetId, fileId);
        assertThat(size, is(file.length()));
    }

    @Test
    public void getFileSize_ZIP以外() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        Long size = client.getFileSize(datasetId, fileId);
        assertThat(size, is(file.length()));
    }

    @Test
    public void getFileSize_ZIP内() throws IOException, ZipException {
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
        Long size = client.getFileSize(datasetId, zFileId);
        assertThat(size, is(zEntry.getSize()));
    }

    @Test
    public void getFileSize_サーバーに接続できない() {
        thrown.expect(ConnectionLostException.class);
        DsmoqClient client = DsmoqClient.create("http://localhost:8081",
                "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649",
                "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
        client.getFileSize("", "");
    }

    @Test
    public void getFileSize_権限違反() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(403));
        DsmoqClient dummyClient = createDummyUserClient();
        File file = new File("README.md");
        Dataset dataset = dummyClient.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = dummyClient.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        DsmoqClient client = create();
        client.getFileSize(datasetId, fileId);
    }

    @Test
    public void getFileSize_存在しないリソース1() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(404));
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        client.getFileSize(datasetId, "");
    }

    @Test
    public void getFileSize_存在しないリソース2() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(404));
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.getFileSize("", fileId);
    }

    @Test
    public void getFileSize_存在しないリソース3() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(404));
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String notExistsId = UUID.randomUUID().toString();
        client.getFileSize(notExistsId, fileId);
    }

    @Test
    public void getFileSize_存在しないリソース4() {
        thrown.expect(HttpStatusException.class);
        thrown.expect(HttpStatusExceptionMatcher.is(404));
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String notExistsId = UUID.randomUUID().toString();
        client.getFileSize(datasetId, notExistsId);
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
}
