package sample;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.xml.bind.DatatypeConverter;

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient;
import jp.ac.nagoya_u.dsmoq.sdk.request.GetRangeParam;
import jp.ac.nagoya_u.dsmoq.sdk.response.DatasetFile;
import jp.ac.nagoya_u.dsmoq.sdk.response.RangeSlice;

/**
 * アプリケーションのエントリークラス
 **/
public class Main {
    public static void main(String[] args) {
        // 接続に必要な情報と、対象となるデータセットのIDは、システムプロパティに格納されています
        // 各情報は以下のように取得できます
        String baseUrl = System.getProperty("jnlp.dsmoq.url");
        String encriptedApiKey = System.getProperty("jnlp.dsmoq.user.apiKey");
        String encriptedSecretKey = System.getProperty("jnlp.dsmoq.user.secretKey");
        String datasetId = System.getProperty("jnlp.dsmoq.dataset.id");

        // APIキーとシークレットキーは、DES-EDE(トリプルDES)で暗号化されているので復号する必要があります
        // 共通鍵にはデータセットIDを用います
        String apiKey = decript(datasetId, encriptedApiKey);
        String secretKey = decript(datasetId, encriptedSecretKey);

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

    public static String decript(String key, String value) {
        try {
            DESedeKeySpec spec = new DESedeKeySpec(key.getBytes());
            SecretKeyFactory skf = SecretKeyFactory.getInstance("DESede");
            SecretKey sk = skf.generateSecret(spec);
            Cipher cipher = Cipher.getInstance("DESede");
            cipher.init(Cipher.DECRYPT_MODE, sk);
            byte[] base64 = DatatypeConverter.parseBase64Binary(value);
            byte[] bytes = cipher.doFinal(base64);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // do something
            throw new RuntimeException(e);
        }
    }
}
