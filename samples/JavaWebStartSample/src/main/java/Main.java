import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.RangeSlice;

/**
 * アプリケーションのエントリークラス
 *
 * Java Web Start 実行の際は、無名パッケージ下のMainクラスが呼び出されます。
 **/
public class Main {
    public static void main(String[] args) {
        // 接続に必要な情報と、対象となるデータセットのIDは、システムプロパティに格納されています
        // 各情報は以下のように取得できます
        String baseUrl = System.getProperty("jnlp.dsmoq.url");
        String apiKey = System.getProperty("jnlp.dsmoq.user.apiKey");
        String secretKey = System.getProperty("jnlp.dsmoq.user.secretKey");
        String datasetId = System.getProperty("jnlp.dsmoq.dataset.id");

        // システムプロパティから取得した情報を使ってDsmoqClientを作成します
        DsmoqClient client = DsmoqClient.create(baseUrl, apiKey, secretKey);

        // 以降はSDKを用いた通常のアプリケーションと同様です
        // この例では、データセット内のファイル一覧を標準出力に表示します
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        System.out.printf("file count: %d%n", files.getSummary().getTotal());
        System.out.println("files:");
        for (DatasetFile file : files.getResults()) {
            System.out.println(file.getName());
        }
    }
}
