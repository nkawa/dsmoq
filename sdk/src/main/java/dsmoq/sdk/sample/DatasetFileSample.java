package dsmoq.sdk.sample;

import dsmoq.sdk.client.DsmoqClient;
import dsmoq.sdk.request.GetDatasetsParam;
import dsmoq.sdk.response.Dataset;
import dsmoq.sdk.response.DatasetsSummary;
import dsmoq.sdk.response.RangeSlice;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DatasetFileSample {
    public static void main(String[] args) {
        // APIキー、シークレットキーの組み合わせでログインするクライアントを作成する
        DsmoqClient client = DsmoqClient.create("http://localhost:8080", "api key", "secret key");
        // データセットを検索する
        RangeSlice<DatasetsSummary> datasetSummaries = client.getDatasets(
                new GetDatasetsParam(
                    Optional.of("test"),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Optional.empty(),
                    Optional.empty()));
        // データセットの詳細を取得する
        List<Dataset> datasets = datasetSummaries.getResults().stream().map(x -> client.getDataset(x.getId())).collect(Collectors.toList());
        List<File> files = new ArrayList<>();

        // 拡張子が.csvのファイルのみダウンロードしてくる
        for (Dataset dataset : datasets) {
            // 拡張子が.csvのファイルのidを控える
            List<String> ids = dataset.getFiles().stream()
                    .filter(x -> x.getName().endsWith(".csv"))
                    .map(x -> x.getId()).collect(Collectors.toList());
            // ダウンロードしてくる
            List<File> fs = ids.stream().map(x -> client.downloadFile(dataset.getId(), x)).collect(Collectors.toList());
            files.addAll(fs);
        }

        List<String> results = new ArrayList<>();

        // csvファイルの行数をカウントし、記録する
        for (File f : files) {
            try (BufferedReader bf = new BufferedReader(new FileReader(f))) {
                int i = 0;
                while (bf.readLine() != null) {
                    i ++;
                }
                results.add(f.getName() + ":" + i);
            } catch (FileNotFoundException e) {
                // do something
            } catch (IOException e) {
                // do something
            }
        }

        // 集計結果をファイルに書き込む
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(new File("summary.txt")))) {
            bw.write("total files: " + files.size());
            for (String line : results) {
                bw.write(line);
            }
        } catch (FileNotFoundException e) {
            // do something
        } catch (IOException e) {
            // do something
        }

    }
}
