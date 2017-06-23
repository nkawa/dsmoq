import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.AccessLevelPrivateCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.AccessLevelPublicCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.AttributeCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetDatasetsConditionParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.NumberOfFilesGreaterThanEqualCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.NumberOfFilesLessThanEqualCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.OwnerEqualCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.OwnerNotEqualCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.QueryContainCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.QueryNotContainCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.SetGuestAccessLevelParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.SizeUnit;
import jp.ac.nagoya_u.dsmoq.sdk.request.TagCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.TotalSizeGreaterThanEqualCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.TotalSizeLessThanEqualCondition;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateDatasetAttributeParam;
import jp.ac.nagoya_u.dsmoq.sdk.request.UpdateDatasetMetaParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.Dataset;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetsSummary;
import jp.ac.nagoya_u.dsmoq.sdk.response.RangeSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class SDKGetDatasetsTest {
    private static DsmoqClient createClient() {
        return DsmoqClient.create("http://localhost:8080",
                "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba",
                "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
    }

    private static class BaseTest {
        DsmoqClient client;

        public void setUp() {
            client = createClient();
        }

        public void tearDown() {
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(new GetDatasetsConditionParam());
            datasets.getResults().forEach(ds -> {
                RangeSlice<DatasetFile> files = client.getDatasetFiles(ds.getId(), new GetRangeParam());
                try {
                    files.getResults().forEach(x -> client.deleteFile(ds.getId(), x.getId()));
                    client.deleteDataset(ds.getId());
                } catch (Exception e) {
                    // do nothing
                }
            });
            client = null;
        }
    }

    /**
     * QueryContainCondition/QueryNotContainCondition
     */
    @RunWith(Theories.class)
    public static class QueryContainConditionTest extends BaseTest {
        @Before
        @Override
        public void setUp() {
            super.setUp();
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @DataPoints
        public static Fixture[] getFixture() {
            return new Fixture[]{
                    // query:未指定、QueryContainCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"),
                            "", 3, true),
                    // query：英数字、該当データセットなし、QueryContainCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"),
                            "datasets", 0, true),
                    // query：英数字、該当データセットあり、QueryContainCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"),
                            "dataset1", 1, true),
                    // query：日本語、該当データセットなし、QueryContainCondition
                    new Fixture(Arrays.asList("データセット1", "データセット2", "データセット3"),
                            "デーセット", 0, true),
                    // query：日本語、該当データセットあり、QueryContainCondition
                    new Fixture(Arrays.asList("データセット01", "データセット02", "データセット3"),
                            "データセット0", 2, true),
                    // query:未指定
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"),
                            "", 3, false),
                    // query：英数字、該当データセットなし
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"),
                            "datasets", 3, false),
                    // query：英数字、該当データセットあり
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"),
                            "dataset1", 2, false),
                    // query：日本語、該当データセットなし
                    new Fixture(Arrays.asList("データセット1", "データセット2", "データセット3"),
                            "デーセット", 3, false),
                    // query：日本語、該当データセットなし
                    new Fixture(Arrays.asList("データセット01", "データセット02", "データセット3"),
                            "データセット0", 1, false),
            };
        }

        @Theory
        public void testQuery(Fixture fixture) {
            fixture.datasetNames.forEach(name -> client.createDataset(name, true, false));

            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            if (fixture.contains) {
                param.add(new QueryContainCondition(fixture.query));
            } else {
                param.add(new QueryNotContainCondition(fixture.query));
            }
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(fixture.datasetCount));
            results.forEach(ds -> assertTrue(fixture.datasetNames.contains(ds.getName())));
        }

        static class Fixture {
            final List<String> datasetNames;
            final String query;
            final int datasetCount;
            final boolean contains;

            Fixture(List<String> datasetNames, String query, int datasetCount, boolean contains) {
                this.datasetNames = datasetNames;
                this.query = query;
                this.datasetCount = datasetCount;
                this.contains = contains;
            }
        }
    }

    /**
     * OwnerEqualCondition/OwnerNotEqualCondition
     */
    @RunWith(Theories.class)
    public static class OwnerEqualConditionTest extends BaseTest {
        @Before
        @Override
        public void setUp() {
            super.setUp();
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @DataPoints
        public static Fixture[] getFixture() {
            return new Fixture[]{
                    // tag：未指定、OwnerEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "", 0, true),
                    // tag：実在ユーザー、該当データセットなし、OwnerEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "dummy2", 0, true),
                    // tag：実在ユーザー、該当データセットあり、OwnerEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "dummy", 3, true),
                    // tag：非実在ユーザー、OwnerEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "dummy1", 0, true),
                    // tag：未指定、OwnerNotEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "", 3, false),
                    // tag：実在ユーザー、該当データセットなし、OwnerNotEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "dummy2", 3, false),
                    // tag：実在ユーザー、該当データセットあり、OwnerNotEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "dummy", 0, false),
                    // tag：非実在ユーザー、OwnerNotEqualCondition
                    new Fixture(Arrays.asList("dataset1", "dataset2", "dataset3"), "dummy1", 3, false),
            };
        }

        @Theory
        public void testQuery(Fixture fixture) {
            fixture.datasetNames.forEach(name -> client.createDataset(name, true, false));

            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            if (fixture.equal) {
                param.add(new OwnerEqualCondition(fixture.owner));
            } else {
                param.add(new OwnerNotEqualCondition(fixture.owner));
            }
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(fixture.datasetCount));
            results.forEach(ds -> assertTrue(fixture.datasetNames.contains(ds.getName())));
        }

        static class Fixture {
            final List<String> datasetNames;
            final String owner;
            final int datasetCount;
            final boolean equal;

            Fixture(List<String> datasetNames, String owner, int datasetCount, boolean equal) {
                this.datasetNames = datasetNames;
                this.owner = owner;
                this.datasetCount = datasetCount;
                this.equal = equal;
            }
        }
    }

    /**
     * OwnerEqualCondition/OwnerNotEqualCondition
     */
    @RunWith(Theories.class)
    public static class TagConditionTest extends BaseTest {
        @Before
        @Override
        public void setUp() {
            super.setUp();
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @DataPoints
        public static Fixture[] getFixture() {
            return new Fixture[]{
                    // tag：未指定
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("tag1", "tag2"));
                        put("dataset2", Collections.singletonList("tag1"));
                        put("dataset3", Arrays.asList("tag1", "tag3", "tag4"));
                    }}, "", 0),
                    // tag：英数字のみ、該当データセットなし
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("tag1", "tag2"));
                        put("dataset2", Collections.singletonList("tag1"));
                        put("dataset3", Arrays.asList("tag1", "tag3", "tag4"));
                    }}, "tag", 0),
                    // tag：英数字のみ、該当データセットあり
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("tag1", "tag2"));
                        put("dataset2", Collections.singletonList("tag2"));
                        put("dataset3", Arrays.asList("tag1", "tag3", "tag4"));
                    }}, "tag1", 2),
                    // tag：日本語、該当データセットなし
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("タグ１", "タグ２"));
                        put("dataset2", Collections.singletonList("タグ２"));
                        put("dataset3", Arrays.asList("タグ１", "タグ３", "タグ４"));
                    }}, "タグ", 0),
                    // tag：日本語、該当データセットあり
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("タグ１", "タグ２"));
                        put("dataset2", Collections.singletonList("タグ２"));
                        put("dataset3", Arrays.asList("タグ１", "タグ３", "タグ４"));
                    }}, "タグ３", 1),
            };
        }

        @Theory
        public void testQuery(Fixture fixture) {
            fixture.datasetInfos.forEach((name, tags) -> {
                Dataset dataset = client.createDataset(name, true, false);

                UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
                param.setName(name);
                param.setDescription("dummy description");
                param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
                param.setAttributes(tags.stream()
                        .map(tag -> new UpdateDatasetAttributeParam(tag, "$tag"))
                        .collect(Collectors.toList())
                );
                client.updateDatasetMetaInfo(dataset.getId(), param);
            });

            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new TagCondition(fixture.tag));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(fixture.datasetCount));
            final Set<String> datasetNameSet = fixture.datasetInfos.keySet();
            results.forEach(ds -> assertThat(datasetNameSet, hasItem(ds.getName())));
        }

        static class Fixture {
            final Map<String, List<String>> datasetInfos;
            final String tag;
            final int datasetCount;

            Fixture(Map<String, List<String>> datasetInfos, String tag, int datasetCount) {
                this.datasetInfos = datasetInfos;
                this.tag = tag;
                this.datasetCount = datasetCount;
            }
        }
    }

    /**
     * AttributeCondition
     */
    @RunWith(Theories.class)
    public static class AttributeConditionTest extends BaseTest {
        @Before
        @Override
        public void setUp() {
            super.setUp();
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @DataPoints
        public static Fixture[] getFixture() {
            return new Fixture[]{
                    // key：未指定、value：未指定
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("attr1", "value1"), new Attribute("attr2", "value2")));
                        put("dataset2", Collections.singletonList(new Attribute("attr3", "value3")));
                        put("dataset3", Arrays.asList(new Attribute("attr3", "value3"), new Attribute("attr4", "value4")));
                    }}, "", "", 3),
                    // key：英数字、value：未指定、該当データセットなし
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("attr1", "value1"), new Attribute("attr2", "value2")));
                        put("dataset2", Collections.singletonList(new Attribute("attr3", "value3")));
                        put("dataset3", Arrays.asList(new Attribute("attr3", "value3"), new Attribute("attr4", "value4")));
                    }}, "attr5", "", 0),
                    // key：英数字、value：未指定、該当データセットあり
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("attr1", "value1"), new Attribute("attr2", "value2")));
                        put("dataset2", Collections.singletonList(new Attribute("attr3", "value3")));
                        put("dataset3", Arrays.asList(new Attribute("attr3", "value3"), new Attribute("attr4", "value4")));
                    }}, "attr3", "", 2),
                    // key：日本語、value：未指定、該当データセットなし
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("属性１", "値１"), new Attribute("属性２", "値２")));
                        put("dataset2", Collections.singletonList(new Attribute("属性３", "値３")));
                        put("dataset3", Arrays.asList(new Attribute("属性３", "値３"), new Attribute("属性４", "値４")));
                    }}, "ぞくせい１", "", 0),
                    // key：日本語、value：未指定、該当データセットあり
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("属性１", "値１"), new Attribute("属性２", "値２")));
                        put("dataset2", Collections.singletonList(new Attribute("属性３", "値３")));
                        put("dataset3", Arrays.asList(new Attribute("属性３", "値３"), new Attribute("属性４", "値４")));
                    }}, "属性１", "", 1),
                    // key：未指定、value：英数字、該当データセットあり
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("attr1", "value1"), new Attribute("attr2", "value2")));
                        put("dataset2", Collections.singletonList(new Attribute("attr3", "value3")));
                        put("dataset3", Arrays.asList(new Attribute("attr3", "value3"), new Attribute("attr4", "value4")));
                    }}, "", "value3", 2),
                    // key：未指定、value：日本語、該当データセットあり
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("属性１", "値１"), new Attribute("属性２", "値２")));
                        put("dataset2", Collections.singletonList(new Attribute("属性３", "値３")));
                        put("dataset3", Arrays.asList(new Attribute("属性３", "値３"), new Attribute("属性４", "値４")));
                    }}, "属性１", "", 1),
                    // key：英数字、value：英数字、該当データセットあり
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("attr1", "value1"), new Attribute("attr2", "value2")));
                        put("dataset2", Collections.singletonList(new Attribute("attr3", "value3")));
                        put("dataset3", Arrays.asList(new Attribute("attr3", "value3"), new Attribute("attr4", "value4")));
                    }}, "attr4", "value4", 1),
                    // key：日本語、value：日本語、該当データセットあり
                    new Fixture(new HashMap<String, List<Attribute>>() {{
                        put("dataset1", Arrays.asList(new Attribute("属性１", "値１"), new Attribute("属性２", "値２")));
                        put("dataset2", Collections.singletonList(new Attribute("属性３", "値３")));
                        put("dataset3", Arrays.asList(new Attribute("属性３", "値３"), new Attribute("属性４", "値４")));
                    }}, "属性３", "値３", 2),
            };
        }

        @Theory
        public void testQuery(Fixture fixture) {
            fixture.datasetInfo.forEach((name, tags) -> {
                Dataset dataset = client.createDataset(name, true, false);

                UpdateDatasetMetaParam param = new UpdateDatasetMetaParam();
                param.setName(name);
                param.setDescription("dummy description");
                param.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
                param.setAttributes(tags.stream()
                        .map(attr -> new UpdateDatasetAttributeParam(attr.key, attr.value))
                        .collect(Collectors.toList())
                );
                client.updateDatasetMetaInfo(dataset.getId(), param);
            });

            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new AttributeCondition(fixture.key, fixture.value));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(fixture.datasetCount));
            final Set<String> datasetNameSet = fixture.datasetInfo.keySet();
            results.forEach(ds -> assertThat(datasetNameSet, hasItem(ds.getName())));
        }

        static class Fixture {
            final Map<String, List<Attribute>> datasetInfo;
            final String key;
            final String value;
            final int datasetCount;

            Fixture(Map<String, List<Attribute>> datasetInfo, String key, String value, int datasetCount) {
                this.datasetInfo = datasetInfo;
                this.key = key;
                this.value = value;
                this.datasetCount = datasetCount;
            }
        }

        static class Attribute {
            final String key;
            final String value;

            Attribute(String key, String value) {
                this.key = key;
                this.value = value;
            }
        }
    }

    /**
     * TotalSizeCondition
     */
    @RunWith(Theories.class)
    public static class TotalSizeConditionTest extends BaseTest {
        @Before
        @Override
        public void setUp() {
            super.setUp();
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @DataPoints
        public static Fixture[] getFixture() {
            return new Fixture[]{
                    // size: 0, operator: le
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Collections.emptyList());
                        put("dataset2", Collections.singletonList(new FileInfo("file1", 1)));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file1", 1), new FileInfo("file10", 10)));
                    }}, 0, SizeUnit.Byte$.MODULE$, 1, true),
                    // size: 100, operator: le、単位：byte、該当データセットなし
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file150", 150), new FileInfo("file60", 60)));
                        put("dataset2", Collections.singletonList(new FileInfo("file101", 101)));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file200", 200), new FileInfo("file1", 1)));
                    }}, 100, SizeUnit.Byte$.MODULE$, 0, true),
                    // size: 100, operator: le、単位：byte、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file50", 50), new FileInfo("file50", 50)));
                        put("dataset2", Collections.singletonList(new FileInfo("file99", 99)));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file1", 1), new FileInfo("file2", 2)));
                    }}, 100, SizeUnit.Byte$.MODULE$, 3, true),
                    // size: 0, operator: ge
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Collections.emptyList());
                        put("dataset2", Collections.singletonList(new FileInfo("file1", 1)));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file1", 1), new FileInfo("file10", 10)));
                    }}, 0, SizeUnit.Byte$.MODULE$, 3, false),
                    // size: 100, operator: ge、単位：byte、該当データセットなし
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file50", 50), new FileInfo("file60", 60)));
                        put("dataset2", Collections.singletonList(new FileInfo("file1", 1)));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file20", 20), new FileInfo("file1", 1)));
                    }}, 100, SizeUnit.Byte$.MODULE$, 0, false),
                    // size: 100, operator: ge、単位：byte、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file150", 150), new FileInfo("file50", 50)));
                        put("dataset2", Collections.singletonList(new FileInfo("file101", 101)));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file1", 1), new FileInfo("file1", 1)));
                    }}, 100, SizeUnit.Byte$.MODULE$, 2, false),
                    // size: 100, operator: le、単位：KB、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file50kb", toKB(50)),
                                new FileInfo("file60kb", toKB(60))));
                        put("dataset2", Collections.singletonList(new FileInfo("file100kb", toKB(100))));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file90kb", toKB(99)),
                                new FileInfo("file1kb", toKB(1))));
                    }}, 100, SizeUnit.KB$.MODULE$, 3, true),
                    // size: 100, operator: ge、単位：KB、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file150kb", toKB(150)),
                                new FileInfo("file50kb", toKB(50))));
                        put("dataset2", Collections.singletonList(new FileInfo("file101kb", toKB(101))));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file1kb", toKB(1)),
                                new FileInfo("file3kb", toKB(3))));
                    }}, 100, SizeUnit.KB$.MODULE$, 2, false),
                    // size: 10, operator: le、単位：MB、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file5mb", toMB(5)),
                                new FileInfo("file5mb", toMB(6))));
                        put("dataset2", Collections.singletonList(new FileInfo("file10mb", toMB(10))));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file9mb", toMB(9)),
                                new FileInfo("file1mb", toMB(1))));
                    }}, 10, SizeUnit.MB$.MODULE$, 3, true),
                    // size: 10, operator: ge、単位：MB、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file15mb", toMB(15)),
                                new FileInfo("file5mb", toMB(5))));
                        put("dataset2", Collections.singletonList(new FileInfo("file11mb", toMB(11))));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file1mb", toMB(1)),
                                new FileInfo("file2mb", toMB(2))));
                    }}, 10, SizeUnit.MB$.MODULE$, 2, false),
                    // size: 1, operator: le、単位：GB、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file500mb", toGB(0.5)),
                                new FileInfo("file400mb", toGB(0.4))));
                        put("dataset2", Collections.singletonList(new FileInfo("file1gb", toGB(1))));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file900mb", toGB(0.9)),
                                new FileInfo("file100mb", toGB(0.1))));
                    }}, 1, SizeUnit.GB$.MODULE$, 3, true),
                    // size: 1, operator: ge、単位：GB、該当データセットあり
                    new Fixture(new HashMap<String, List<FileInfo>>() {{
                        put("dataset1", Arrays.asList(
                                new FileInfo("file1.5gb", toGB(1.5)),
                                new FileInfo("file500mb", toGB(0.5))));
                        put("dataset2", Collections.singletonList(new FileInfo("file1.1gb", toGB(1.1))));
                        put("dataset3", Arrays.asList(
                                new FileInfo("file100mb", toGB(0.1)),
                                new FileInfo("file200mb", toGB(0.2))));
                    }}, 1, SizeUnit.GB$.MODULE$, 2, false),
            };
        }

        private static long toKB(double value) {
            return (long) (value * 1024);
        }

        private static long toMB(double value) {
            return (long) (value * 1024 * 1024);
        }

        private static long toGB(double value) {
            return (long) (value * 1024 * 1024 * 1024);
        }

        @Rule
        public TemporaryFolder tmpFolder = new TemporaryFolder();

        @Theory
        public void testQuery(Fixture fixture) throws Exception {
            fixture.datasetInfo.forEach((name, fileInfoList) -> {
                Dataset dataset = client.createDataset(name, true, false);
                File[] files = fileInfoList.stream()
                        .map(fileInfo -> createFile(name, fileInfo.size))
                        .filter(Objects::nonNull)
                        .toArray(File[]::new);
                if (files.length > 0) {
                    client.addFiles(dataset.getId(), files);
                }
            });

            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            if (fixture.le) {
                param.add(new TotalSizeLessThanEqualCondition(fixture.size, fixture.unit));
            } else {
                param.add(new TotalSizeGreaterThanEqualCondition(fixture.size, fixture.unit));
            }
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(fixture.datasetCount));
            final Set<String> datasetNameSet = fixture.datasetInfo.keySet();
            results.forEach(ds -> assertThat(datasetNameSet, hasItem(ds.getName())));
        }

        private File createFile(String name, long size) {
            try {
                File file = tmpFolder.newFile(name);
                try (RandomAccessFile raf = new RandomAccessFile(file.getAbsolutePath(), "rw")) {
                    raf.setLength(size);
                    return file;
                }
            } catch (IOException e) {
                assertFalse(false);
                return null;
            }
        }

        static class Fixture {
            final Map<String, List<FileInfo>> datasetInfo;
            final double size;
            final SizeUnit unit;
            final int datasetCount;
            final boolean le;

            Fixture(Map<String, List<FileInfo>> datasetInfo, double size, SizeUnit unit, int datasetCount, boolean le) {
                this.datasetInfo = datasetInfo;
                this.size = size;
                this.unit = unit;
                this.datasetCount = datasetCount;
                this.le = le;
            }
        }

        static class FileInfo {
            final String name;
            final long size;

            FileInfo(String name, long size) {
                this.name = name;
                this.size = size;
            }
        }
    }

    /**
     * NumberOfFilesLessThanEqualCondition/NumberOfFilesGreaterThanEqualCondition
     */
    @RunWith(Theories.class)
    public static class NumberOfFilesConditionTest extends BaseTest {
        @Before
        @Override
        public void setUp() {
            super.setUp();
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @DataPoints
        public static Fixture[] getFixture() {
            return new Fixture[]{
                    // 指定数 <= ファイル数、該当データセットなし
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("file11", "file12"));
                        put("dataset2", Arrays.asList("file21", "file22", "file23", "file24"));
                        put("dataset3", Arrays.asList("file31", "file32", "file33"));
                    }}, 1, true, 0),
                    // 指定数 <= ファイル数、該当データセットあり
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("file11", "file12"));
                        put("dataset2", Collections.singletonList("file21"));
                        put("dataset3", Arrays.asList("file31", "file32", "file33"));
                    }}, 2, true, 2),
                    // 指定数 >= ファイル数、該当データセットなし
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("file11", "file12"));
                        put("dataset2", Arrays.asList("file21", "file22", "file23", "file24"));
                        put("dataset3", Arrays.asList("file31", "file32", "file33"));
                    }}, 5, false, 0),
                    // 指定数 >= ファイル数、該当データセットあり
                    new Fixture(new HashMap<String, List<String>>() {{
                        put("dataset1", Arrays.asList("file11", "file12"));
                        put("dataset2", Arrays.asList("file21", "file22", "file23", "file24"));
                        put("dataset3", Arrays.asList("file31", "file32", "file33"));
                    }}, 2, false, 3),
            };
        }

        @Rule
        public TemporaryFolder tmpFolder = new TemporaryFolder();

        @Theory
        public void testQuery(Fixture fixture) {
            fixture.datasetInfo.forEach((name, fileNames) -> {
                Dataset dataset = client.createDataset(name, true, false);
                File[] files = fileNames.stream()
                        .map(this::createFile)
                        .filter(Objects::nonNull)
                        .toArray(File[]::new);
                if (files.length > 0) {
                    client.addFiles(dataset.getId(), files);
                }
            });

            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            if (fixture.le) {
                param.add(new NumberOfFilesLessThanEqualCondition(fixture.count));
            } else {
                param.add(new NumberOfFilesGreaterThanEqualCondition(fixture.count));
            }
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(fixture.datasetCount));
            final Set<String> datasetNameSet = fixture.datasetInfo.keySet();
            results.forEach(ds -> assertThat(datasetNameSet, hasItem(ds.getName())));
        }

        private File createFile(String name) {
            try {
                File file = tmpFolder.newFile(name);
                try (RandomAccessFile raf = new RandomAccessFile(file.getAbsolutePath(), "rw")) {
                    raf.setLength((long) 10);
                    return file;
                }
            } catch (IOException e) {
                assertFalse(false);
                return null;
            }
        }

        static class Fixture {
            final Map<String, List<String>> datasetInfo;
            final int count;
            final int datasetCount;
            final boolean le;

            Fixture(Map<String, List<String>> datasetInfo, int count, boolean le, int datasetCount) {
                this.datasetInfo = datasetInfo;
                this.count = count;
                this.datasetCount = datasetCount;
                this.le = le;
            }
        }
    }

    /**
     * NumberOfFilesLessThanEqualCondition/NumberOfFilesGreaterThanEqualCondition
     */
    @RunWith(Theories.class)
    public static class PublicConditionTest extends BaseTest {
        @Before
        @Override
        public void setUp() {
            super.setUp();
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @DataPoints
        public static Fixture[] getFixture() {
            return new Fixture[]{
                    // public、該当データなし
                    new Fixture(new HashMap<String, Integer>() {{
                        put("dataset1", 0);
                        put("dataset2", 0);
                        put("dataset3", 0);
                    }}, true, 0),
                    // public、該当データあり
                    new Fixture(new HashMap<String, Integer>() {{
                        put("dataset1", 2);
                        put("dataset2", 2);
                        put("dataset3", 2);
                    }}, true, 3),
                    // private、該当データなし
                    new Fixture(new HashMap<String, Integer>() {{
                        put("dataset1", 0);
                        put("dataset2", 0);
                        put("dataset3", 0);
                    }}, false, 3),
                    // private、該当データあり
                    new Fixture(new HashMap<String, Integer>() {{
                        put("dataset1", 2);
                        put("dataset2", 2);
                        put("dataset3", 2);
                    }}, false, 0),
            };
        }

        @Rule
        public TemporaryFolder tmpFolder = new TemporaryFolder();

        @Theory
        public void testQuery(Fixture fixture) {
            fixture.datasetInfo.forEach((name, accessLevel) -> {
                Dataset dataset = client.createDataset(name, true, false);
                client.changeGuestAccessLevel(dataset.getId(), new SetGuestAccessLevelParam(accessLevel));
            });

            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            if (fixture.isPublic) {
                param.add(new AccessLevelPublicCondition());
            } else {
                param.add(new AccessLevelPrivateCondition());
            }
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(fixture.datasetCount));
            final Set<String> datasetNameSet = fixture.datasetInfo.keySet();
            results.forEach(ds -> assertThat(datasetNameSet, hasItem(ds.getName())));
        }

        static class Fixture {
            final Map<String, Integer> datasetInfo;
            final boolean isPublic;
            final int datasetCount;

            Fixture(Map<String, Integer> datasetInfo, boolean isPublic, int datasetCount) {
                this.datasetInfo = datasetInfo;
                this.isPublic = isPublic;
                this.datasetCount = datasetCount;
            }
        }
    }

    @RunWith(Theories.class)
    public static class SomeConditionTest extends BaseTest {
        private Dataset d1;
        private Dataset d2;
        private Dataset d3;
        private Dataset d4;
        private Dataset d5;
        private Dataset d6;

        @Before
        @Override
        public void setUp() {
            super.setUp();
            d1 = client.createDataset("dataset1", true, false);
            d2 = client.createDataset("dataset2", true, false);
            d3 = client.createDataset("dataset3", true, false);
            d4 = client.createDataset("データセット４", true, false);
            d5 = client.createDataset("データセット５", true, false);
            d6 = client.createDataset("データセット６", true, false);

            UpdateDatasetMetaParam d1MetaParam = new UpdateDatasetMetaParam();
            d1MetaParam.setName(d1.getMeta().getName());
            d1MetaParam.setDescription("dummy description");
            d1MetaParam.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
            d1MetaParam.setAttributes(Arrays.asList(
                    new UpdateDatasetAttributeParam("tag1", "$tag"),
                    new UpdateDatasetAttributeParam("タグ２", "$tag")
            ));
            client.updateDatasetMetaInfo(d1.getId(), d1MetaParam);

            UpdateDatasetMetaParam d4MetaParam = new UpdateDatasetMetaParam();
            d4MetaParam.setName(d4.getMeta().getName());
            d4MetaParam.setDescription("dummy description");
            d4MetaParam.setLicense("1050f556-7fee-4032-81e7-326e5f1b82fb");
            d4MetaParam.setAttributes(Arrays.asList(
                    new UpdateDatasetAttributeParam("attr1", "value1"),
                    new UpdateDatasetAttributeParam("属性２", "値２")
            ));
            client.updateDatasetMetaInfo(d4.getId(), d4MetaParam);
        }

        @After
        @Override
        public void tearDown() {
            super.tearDown();
        }

        @Test
        public void 複数条件指定_該当なし() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new QueryContainCondition("data"));
            param.add(new QueryContainCondition("set"));
            param.add(new QueryContainCondition("5"));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(0));
        }

        @Test
        public void 複数条件指定_該当あり() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new QueryContainCondition("set"));
            param.add(new OwnerEqualCondition("dummy"));
            param.add(new TagCondition("タグ２"));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(1));
            assertThat(results.get(0).getName(), is("dataset1"));
        }

        @Test
        public void 複数条件指定_OR含む_該当なし() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new QueryContainCondition("data"));
            param.add(new QueryContainCondition("sets"));
            param.or();
            param.add(new OwnerEqualCondition("dummy2"));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(0));
        }

        @Test
        public void 複数条件指定_OR含む_一致する条件が一つ以上あり() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new QueryContainCondition("data"));
            param.add(new QueryContainCondition("set"));
            param.or();
            param.add(new TagCondition("タグ２"));
            param.or();
            param.add(new OwnerEqualCondition("dummy2"));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(3));
            List<String> datasetNameList = results.stream().map(DatasetsSummary::getName).collect(Collectors.toList());
            assertThat(datasetNameList, hasItem("dataset1"));
            assertThat(datasetNameList, hasItem("dataset2"));
            assertThat(datasetNameList, hasItem("dataset3"));
        }

        @Test
        public void 複数条件指定_OR含む_すべての検索条件が一致() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new QueryContainCondition("data"));
            param.add(new QueryContainCondition("set"));
            param.or();
            param.add(new TagCondition("タグ２"));
            param.or();
            param.add(new AttributeCondition("attr1", "value1"));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(4));
            List<String> datasetNameList = results.stream().map(DatasetsSummary::getName).collect(Collectors.toList());
            assertThat(datasetNameList, hasItem("dataset1"));
            assertThat(datasetNameList, hasItem("dataset2"));
            assertThat(datasetNameList, hasItem("dataset3"));
            assertThat(datasetNameList, hasItem("データセット４"));
        }

        @Test
        public void 複数条件指定_OR含む_すべての検索条件が一致_ORが先頭にあり() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.or();
            param.add(new QueryContainCondition("data"));
            param.add(new QueryContainCondition("set"));
            param.or();
            param.add(new TagCondition("タグ２"));
            param.or();
            param.add(new AttributeCondition("attr1", "value1"));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(4));
            List<String> datasetNameList = results.stream().map(DatasetsSummary::getName).collect(Collectors.toList());
            assertThat(datasetNameList, hasItem("dataset1"));
            assertThat(datasetNameList, hasItem("dataset2"));
            assertThat(datasetNameList, hasItem("dataset3"));
            assertThat(datasetNameList, hasItem("データセット４"));
        }

        @Test
        public void 複数条件指定_OR含む_すべての検索条件が一致_ORが末尾にあり() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new QueryContainCondition("data"));
            param.add(new QueryContainCondition("set"));
            param.or();
            param.add(new TagCondition("タグ２"));
            param.or();
            param.add(new AttributeCondition("attr1", "value1"));
            param.or();
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(4));
            List<String> datasetNameList = results.stream().map(DatasetsSummary::getName).collect(Collectors.toList());
            assertThat(datasetNameList, hasItem("dataset1"));
            assertThat(datasetNameList, hasItem("dataset2"));
            assertThat(datasetNameList, hasItem("dataset3"));
            assertThat(datasetNameList, hasItem("データセット４"));
        }

        @Test
        public void 複数条件指定_OR含む_すべての検索条件が一致_ORが連続() {
            GetDatasetsConditionParam param = new GetDatasetsConditionParam();
            param.add(new QueryContainCondition("data"));
            param.add(new QueryContainCondition("set"));
            param.or();
            param.or();
            param.add(new TagCondition("タグ２"));
            param.or();
            param.or();
            param.or();
            param.add(new AttributeCondition("attr1", "value1"));
            RangeSlice<DatasetsSummary> datasets = client.getDatasets(param);

            List<DatasetsSummary> results = datasets.getResults();
            assertThat(results.size(), is(4));
            List<String> datasetNameList = results.stream().map(DatasetsSummary::getName).collect(Collectors.toList());
            assertThat(datasetNameList, hasItem("dataset1"));
            assertThat(datasetNameList, hasItem("dataset2"));
            assertThat(datasetNameList, hasItem("dataset3"));
            assertThat(datasetNameList, hasItem("データセット４"));
        }

    }

}
