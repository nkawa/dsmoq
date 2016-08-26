import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.RangeSlice;

public class Main {
    public static void main(String[] args) {
        String baseUrl = System.getProperty("jnlp.dsmoq.url");
        String apiKey = System.getProperty("jnlp.dsmoq.user.apiKey");
        String secretKey = System.getProperty("jnlp.dsmoq.user.secretKey");
        String datasetId = System.getProperty("jnlp.dsmoq.dataset.id");

        DsmoqClient client = DsmoqClient.create(baseUrl, apiKey, secretKey);
        RangeSlice<DatasetFile> files = client.getDatasetFiles(datasetId, new GetRangeParam());
        System.out.printf("file count: %d%n", files.getSummary().getTotal());
        System.out.println("files:");
        for (DatasetFile file : files.getResults()) {
            System.out.println(file.getName());
        }
    }
}
