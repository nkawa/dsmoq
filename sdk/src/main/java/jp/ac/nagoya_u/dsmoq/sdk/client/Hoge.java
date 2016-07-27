package jp.ac.nagoya_u.dsmoq.sdk.client;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.*;
import jp.ac.nagoya_u.dsmoq.sdk.response.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Hoge {
	public static void main(String[] args) throws Exception {
		DsmoqClient client = DsmoqClient.create("http://localhost:8080", "7d8d8cf12ef0d12d057b01765779c56a5f8a7e1330a41be189114935660ef1ba", "22698424fa67a56cd6d916988fd824c6f999d18a934831de83e15c3490376372");
		test1(client);
		test2(client);
		tearDown(client);
	}
	static void test1(DsmoqClient client) throws Exception {
		File original = Files.createFile(Paths.get("表予申能十ソ.txt")).toFile();
		client.createDataset(true, false, new File("表予申能十ソ.txt"));
		
		List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
		String datasetId = summaries.stream().findFirst().get().getId();
		RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
		String fileId = files.getResults().get(0).getId();
		Path dir = Paths.get("temp");
		if (!dir.toFile().exists()) {
			Files.createDirectory(dir);
		}
		File file = client.downloadFile(datasetId, fileId, "temp");
		System.out.println(file);
		original.delete();
		file.delete();
		dir.toFile().delete();
	}
	static void test2(DsmoqClient client) throws IOException {
		Path original = Paths.get("testdata", "multibyte_utf8_nobom.txt");
		client.createDataset(true, false, original.toFile());
		List<DatasetsSummary> summaries = client.getDatasets(new GetDatasetsParam()).getResults();
		String datasetId = summaries.stream().findFirst().get().getId();
		RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
		String fileId = files.getResults().get(0).getId();
		Path dir = Paths.get("temp");
		if (! dir.toFile().exists()) {
			Files.createDirectory(dir);
		}
		File file = client.downloadFile(datasetId, fileId, "temp");
		Path downloaded = Paths.get("temp", "multibyte_utf8_nobom.txt");
		System.out.printf("original:%n%s%n", String.join("\n", Files.readAllLines(original)));
		System.out.printf("downloaded:%n%s%n", String.join("\n", Files.readAllLines(downloaded)));
		file.delete();
		dir.toFile().delete();
	}
	static void tearDown(DsmoqClient client) {
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
}
