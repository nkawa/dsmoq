
import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.*;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;
import jp.ac.nagoya_u.dsmoq.sdk.util.*;
import org.apache.http.conn.HttpHostConnectException;
import org.joda.time.DateTime;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class SDKHeadTest {
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

    @Test(expected = NullPointerException.class)
    public void getFileSize_nullチェック1() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        client.getFileSize(null, fileId);
    }

    @Test(expected = NullPointerException.class)
    public void getFileSize_nullチェック2() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        client.getFileSize(datasetId, null);
    }

    @Test(expected = HttpStatusException.class)
    public void getFileSize_存在しないリソース1() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        try {
            client.getFileSize(datasetId, "");
        } catch (HttpStatusException e) {
            assertThat(e.getMessage(), is("http_status=404"));
            throw e;
        }
    }

    @Test(expected = HttpStatusException.class)
    public void getFileSize_存在しないリソース2() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        try {
            client.getFileSize("", fileId);
        } catch (HttpStatusException e) {
            assertThat(e.getMessage(), is("http_status=404"));
            throw e;
        }
    }

    @Test(expected = HttpStatusException.class)
    public void getFileSize_存在しないリソース3() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String notExistsId = UUID.randomUUID().toString();
        try {
            client.getFileSize(notExistsId, fileId);
        } catch (HttpStatusException e) {
            assertThat(e.getMessage(), is("http_status=403"));
            throw e;
        }
    }

    @Test(expected = HttpStatusException.class)
    public void getFileSize_存在しないリソース4() {
        DsmoqClient client = create();
        File file = new File("README.md");
        Dataset dataset = client.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        String notExistsId = UUID.randomUUID().toString();
        try {
            client.getFileSize(datasetId, notExistsId);
        } catch (HttpStatusException e) {
            assertThat(e.getMessage(), is("http_status=404"));
            throw e;
        }
    }

    @Test(expected = HttpStatusException.class)
    public void getFileSize_権限違反() {
        DsmoqClient dummyClient = createDummyUserClient();
        File file = new File("README.md");
        Dataset dataset = dummyClient.createDataset(true, false, file);
        String datasetId = dataset.getId();
        RangeSlice<DatasetFile> files = dummyClient.getDatasetFiles(datasetId, new GetRangeParam());
        String fileId = files.getResults().get(0).getId();
        DsmoqClient client = create();
        try {
            client.getFileSize(datasetId, fileId);
        } catch (HttpStatusException e) {
            assertThat(e.getMessage(), is("http_status=403"));
            throw e;
        }
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

    public static DsmoqClient create() {
        return DsmoqClient.create("http://localhost:8080", "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba", "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
    }

    public static DsmoqClient createDummyUserClient() {
        return DsmoqClient.create("http://localhost:8080", "3d2357cd53e8738ae21fbc86e15bd441c497191cf785163541ffa907854d2649", "731cc0646e8012632f58bb7d1912a77e8072c7f128f2d09f0bebc36ac0c1a579");
    }
}
