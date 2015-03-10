package dsmoq.sdk.client;

import dsmoq.sdk.http.*;
import dsmoq.sdk.request.*;
import dsmoq.sdk.response.*;
import dsmoq.sdk.util.ApiFailedException;
import dsmoq.sdk.util.JsonUtil;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by s.soyama on 2015/03/06.
 */
public class DatasetClient {
    private DsmoqClient client;

    public DatasetClient(DsmoqClient client) {
        this.client = client;
    }

    /**
     * Datasetを検索する。(GET /api/datasets相当)
     * @param param Dataset検索に使用するパラメタ
     * @return 検索結果
     */
    public RangeSlice<DatasetsSummary> getDatasets(GetDatasetsParam param) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + "/api/datasets?d=" + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasets(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * Datasetを取得する。(GET /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     * @return 取得結果
     */
    public Dataset getDataset(String datasetId) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/api/datasets/%s", datasetId))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDataset(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param files Datasetに設定するファイル(複数可)
     * @return 作成したDataset
     */
    public Dataset createDataset(boolean saveLocal, boolean saveS3, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + "/api/datasets"))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("file[]", file));
                builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
                builder.addTextBody("saveS3", saveS3 ? "true" : "false");
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDataset(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * Datasetを作成する。(POST /api/datasets相当)
     * @param saveLocal ローカルに保存するか否か
     * @param saveS3 Amazon S3に保存するか否か
     * @param name データセットの名前
     * @return 作成したDataset
     */
    public Dataset createDataset(boolean saveLocal, boolean saveS3, String name) {
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + "/api/datasets"))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("name", name);
                builder.addTextBody("saveLocal", saveLocal ? "true" : "false");
                builder.addTextBody("saveS3", saveS3 ? "true" : "false");
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDataset(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * Datasetにファイルを追加する。(POST /api/datasets/${dataset_id}/files相当)
     * @param datasetId DatasetID
     * @param files Datasetに追加するファイル(複数可)
     * @return 追加したファイルの情報
     */
    public DatasetAddFiles addFiles(String datasetId, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + String.format("/api/datasets/%s/files", datasetId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("files", file));
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetAddFiles(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ファイルを更新する。(POST /api/datasets/${dataset_id}/files/${file_id}相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param file 更新対象のファイル
     * @return 更新されたファイル情報
     */
    public DatasetFile updateFile(String datasetId, String fileId, File file) {
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addBinaryBody("file", file);
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetFile(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * ファイル情報を更新する。(POST /api/datasets/${dataset_id}/files/${file_id}/metadata相当)
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param param ファイル更新情報
     * @return 更新したファイル情報
     */
    public DatasetFile updateFileMetaInfo(String datasetId, String fileId, UpdateFileMetaParam param) {
        try (AutoHttpPut request = new AutoHttpPut((this.client.getBaseUrl() + String.format("/api/datasets/%s/files/%s/metadata", datasetId, fileId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetFile(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットからファイルを削除する。（DELETE /api/datasets/${dataset_id}/files/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     */
    public void deleteFile(String datasetId, String fileId) {
        try (AutoHttpDelete request = new AutoHttpDelete((this.client.getBaseUrl() + String.format("/api/datasets/%s/files/%s", datasetId, fileId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットの情報を更新する。(PUT /api/datasets.${dataset_id}/metadata相当)
     * @param datasetId DatasetID
     * @param param データセット更新情報
     */
    public void updateDatasetMetaInfo(String datasetId, UpdateDatasetMetaParam param) {
        try (AutoHttpPut request = new AutoHttpPut(this.client.getBaseUrl() + String.format("/api/datasets/%s/metadata", datasetId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットに画像を追加する。（POST /api/datasets/${dataset_id}/image相当）
     * @param datasetId DatasetID
     * @param files 追加する画像ファイル
     * @return 追加した画像情報
     */
    public DatasetAddImages addImagesToDataset(String datasetId, File... files) {
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + String.format("/api/datasets/%s/images", datasetId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                Arrays.asList(files).stream().forEach(file -> builder.addBinaryBody("images", file));
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetAddImages(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットに一覧で表示するメイン画像を設定する。（PUT /api/datasets/${dataset_id}/image/primary相当）
     * @param datasetId DatasetID
     * @param param メイン画像指定情報
     */
    public void setPrimaryImageToDataset(String datasetId, SetPrimaryImageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(this.client.getBaseUrl() + String.format("/api/datasets/%s/images/primary", datasetId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットから画像を削除する。（DELETE /api/datasets/${dataset_id}/image/${image_id}相当）
     * @param datasetId DatasetID
     * @param imageId 画像ID
     * @return 画像削除後のデータセットのメイン画像情報
     */
    public DatasetDeleteImage deleteImageToDataset(String datasetId, String imageId) {
        try (AutoHttpDelete request = new AutoHttpDelete(this.client.getBaseUrl() + String.format("/api/datasets/%s/images/%s", datasetId, imageId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetDeleteImage(json);
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットの画像一覧を取得する。
     * @param datasetId データセットID
     * @param param 一覧取得情報
     * @return データセットの画像一覧
     */
    public RangeSlice<DatasetGetImage> getDatasetImage(String datasetId, GetRangeParam param) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/api/datasets/%s/images?d=", datasetId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetGetImage(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットのアクセス権一覧を取得する。
     * @param datasetId データセットID
     * @param param 一覧取得情報
     * @return データセットのアクセス権一覧
     */
    public RangeSlice<DatasetOwnership> getAccessLevel(String datasetId, GetRangeParam param) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/api/datasets/%s/acl?d=", datasetId) + URLEncoder.encode(param.toJsonString(), "UTF-8"))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetOwnership(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットのアクセス権を変更する。（POST /api/datasets/${dataset_id}/acl相当）
     * @param datasetId DatasetID
     * @param params アクセス権制御情報
     * @return 変更後のアクセス権情報
     */
    public DatasetOwnerships changeAccessLevel(String datasetId, List<SetAccessLevelParam> params) {
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + String.format("/api/datasets/%s/acl", datasetId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> p = new ArrayList<>();
                p.add(new BasicNameValuePair("d", SetAccessLevelParam.toJsonString(params)));
                request.setEntity(new UrlEncodedFormEntity(p, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetOwnerships(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットのゲストアカウントでのアクセス権を設定する。（PUT /api/datasets/${dataset_id}/guest_access相当）
     * @param datasetId DatasetID
     * @param param ゲストアカウントでのアクセス権設定情報
     */
    public void changeGuestAccessLevel(String datasetId, SetGuestAccessLevelParam param) {
        try (AutoHttpPut request = new AutoHttpPut(this.client.getBaseUrl() + String.format("/api/datasets/%s/guest_access", datasetId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットを削除する。(DELETE /api/datasets/${dataset_id}相当)
     * @param datasetId DatasetID
     */
    public void deleteDataset(String datasetId) {
        try (AutoHttpDelete request = new AutoHttpDelete(this.client.getBaseUrl() + String.format("/api/datasets/%s", datasetId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットの保存先を変更する。(PUT /api/datasets/${dataset_id}/storage相当)
     * @param datasetId DatasetID
     * @param param 保存先変更情報
     * @return 変更タスクの情報
     */
    public DatasetTask changeDatasetStorage(String datasetId, ChangeStorageParam param) {
        try (AutoHttpPut request = new AutoHttpPut(this.client.getBaseUrl() + String.format("/api/datasets/%s/storage", datasetId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("d", param.toJsonString()));
                request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toDatasetTask(json);
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットをコピーします。
     * @param datasetId DatasetID
     * @return コピーしたDatasetID
     */
    public String copyDataset(String datasetId) {
        try (AutoHttpPost request = new AutoHttpPost(this.client.getBaseUrl() + String.format("/api/datasets/%s/copy", datasetId))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                return JsonUtil.toCopiedDataset(json).getDatasetId();
            }
        } catch (IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * CSVファイルからAttributeを読み込む。
     * @param datasetId DatasetID
     * @param file AttributeをインポートするCSVファイル
     */
    public void importAttribute(String datasetId, File file) {
        try (AutoHttpPost request = new AutoHttpPost((this.client.getBaseUrl() + String.format("/api/datasets/%s/attributes/import", datasetId)))) {
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addBinaryBody("file", file);
                request.setEntity(builder.build());
                HttpResponse response = client.execute(request);
                String json = EntityUtils.toString(response.getEntity());
                JsonUtil.statusCheck(json);
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * CSVファイルにAttributeを出力する。
     * @param datasetId DatasetID
     * @param downloadDirectory 出力先ディレクトリ
     * @return CSVファイル
     */
    public File exportAttribute(String datasetId, String downloadDirectory) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/api/datasets/%s/attributes/export", datasetId))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);

                File file = Paths.get(downloadDirectory, "export.csv").toFile();
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    response.getEntity().writeTo(fos);
                }
                return file;
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットからファイルをダウンロードする。（GET /files/${dataset_id}/${file_id}相当）
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル情報
     */
    public File downloadFile(String datasetId, String fileId, String downloadDirectory) {
        try (AutoHttpGet request = new AutoHttpGet(this.client.getBaseUrl() + String.format("/files/%s/%s", datasetId, fileId))){
            try(AutoCloseHttpClient client = this.client.createHttpClient()) {
                this.client.addAuthorizationHeader(request);
                HttpResponse response = client.execute(request);

                Dataset dataset = getDataset(datasetId);
                DatasetFile targetFile = dataset.getFiles().stream().filter(x -> x.getId().equals(fileId)).findFirst().get();

                File file = Paths.get(downloadDirectory, targetFile.getName()).toFile();
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    response.getEntity().writeTo(fos);
                }
                return file;
            }
        } catch(IOException e) {
            throw new ApiFailedException(e.getMessage(), e);
        }
    }

    /**
     * データセットから一時ディレクトリにファイルをダウンロードする。
     * @param datasetId DatasetID
     * @param fileId ファイルID
     * @return ダウンロードしたファイル
     */
    public File downloadFile(String datasetId, String fileId) {
        File temp = new File("temp");
        if (! temp.exists()) temp.mkdir();
        return downloadFile(datasetId, fileId, "temp");
    }

    /**
     * データセットからすべてのファイルをダウンロードする。
     * @param datasetId DatasetID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllFiles(String datasetId, String downloadDirectory) {
        Dataset dataset = getDataset(datasetId);
        List<DatasetFile> datasetFiles = dataset.getFiles();
        return datasetFiles.stream().map(file -> downloadFile(datasetId, file.getId(), downloadDirectory)).collect(Collectors.toList());
    }

    /**
     * データセットから一時ディレクトリにすべてのファイルをダウンロードする。
     * @param datasetId DatasetID
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllFiles(String datasetId) {
        File temp = new File("temp");
        if (! temp.exists()) temp.mkdir();
        return downloadAllFiles(datasetId, "temp");
    }

    /**
     * データセットからすべてのZipされたファイルをダウンロードする。
     * @param datasetId DatasetID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllZipedFiles(String datasetId, String downloadDirectory) {
        Dataset dataset = getDataset(datasetId);
        Stream<DatasetZipedFile> zipedFiles = dataset.getFiles().stream().flatMap(f -> f.getZipedFiles().stream());
        return zipedFiles.map(file -> downloadFile(datasetId, file.getId(), downloadDirectory)).collect(Collectors.toList());
    }

    /**
     * データセットから一時ディレクトリにすべてのZipされたファイルをダウンロードする。
     * @param datasetId DatasetID
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllZipedFiles(String datasetId) {
        File temp = new File("temp");
        if (! temp.exists()) temp.mkdir();
        return downloadAllZipedFiles(datasetId, "temp");
    }

    /**
     * データセットからすべてのファイルをダウンロードする。Zipファイルは圧縮されたファイルのみを取得する。
     * @param datasetId DatasetID
     * @param downloadDirectory ダウンロード先のディレクトリ
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllExpandedFiles(String datasetId, String downloadDirectory) {
        Dataset dataset = getDataset(datasetId);
        List<String> datasetFiles = dataset.getFiles().stream().filter(f -> ! f.isZip()).map(f -> f.getId()).collect(Collectors.toList());
        List<String> zipedFiles = dataset.getFiles().stream().flatMap(f -> f.getZipedFiles().stream()).map(f -> f.getId()).collect(Collectors.toList());
        datasetFiles.addAll(zipedFiles);
        return datasetFiles.stream().map(id -> downloadFile(datasetId, id, downloadDirectory)).collect(Collectors.toList());
    }

    /**
     * データセットから一時ディレクトリにすべてのファイルをダウンロードする。Zipファイルは圧縮されたファイルのみを取得する。
     * @param datasetId DatasetID
     * @return ダウンロードしたファイル
     */
    public List<File> downloadAllExpandedFiles(String datasetId) {
        File temp = new File("temp");
        if (! temp.exists()) temp.mkdir();
        return downloadAllExpandedFiles(datasetId, "temp");
    }

}
