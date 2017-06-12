package dsmoq.logic

import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESedeKeySpec
import javax.xml.bind.DatatypeConverter

import scala.xml.Elem
import scala.xml.Node
import scala.xml.XML
import scala.xml.parsing.ConstructingParser
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer

import org.scalatra.servlet.FileItem

import dsmoq.AppConf

/**
 * アプリのファイルを処理するオブジェクト
 */
object AppManager {

  /** JNLPファイルのデフォルトエンコード */
  val DEFAULT_JNLP_CHAESAET = StandardCharsets.UTF_8

  /**
   * アプリのJARファイルを保存する。
   *
   * @param appId アプリID
   * @param file アプリのJARファイル
   * @throws IOException 入出力エラーが発生した場合
   */
  def upload(appId: String, file: FileItem): Unit = {
    val appDir = Paths.get(AppConf.appDir, "upload").toFile
    if (!appDir.exists()) {
      appDir.mkdirs()
    }
    // TODO: ファイル存在時の挙動確認
    file.write(appDir.toPath.resolve(appId).toFile)
  }

  /**
   * アプリのJARファイルを削除する。
   *
   * @param appId アプリID
   * @throws IOException 入出力エラーが発生した場合
   */
  def delete(appId: String): Unit = {
    val fullPath = Paths.get(AppConf.appDir, "upload", appId).toFile
    if (!fullPath.exists()) {
      throw new RuntimeException("file not found")
    }
    val result = new File(fullPath.toString).delete()
    if (!result) {
      throw new IOException("file cannot delete")
    }
  }

  /**
   * アプリのJARファイルを取得する。
   *
   * @param appId アプリID
   * @return アプリのJARファイル
   * @throws RuntimeException ファイルが存在しない場合
   */
  def download(appId: String): File = {
    val fullPath = Paths.get(AppConf.appDir, "upload", appId).toFile
    if (!fullPath.exists()) {
      throw new RuntimeException("file not found")
    }
    new File(fullPath.toString)
  }

  /**
   * アプリのJNLPファイルのURLを取得する。
   *
   * @param datasetId データセットID
   * @param userId ユーザID
   * @return JNLPファイルのURL
   */
  def getJnlpUrl(datasetId: String, userId: String): String = {
    s"${AppConf.appDownloadRoot}${userId}/${datasetId}.jnlp"
  }

  /**
   * アプリのJARファイルのURLを取得する。
   *
   * @param datasetId データセットID
   * @param userId ユーザID
   * @return JARファイルのURL
   */
  def getJarUrl(datasetId: String, userId: String): String = {
    s"${AppConf.appDownloadRoot}${userId}/${datasetId}.jar"
  }

  /**
   * アプリのJNLPファイルを取得する。
   *
   * @param datasetId データセットID
   * @param userId 利用ユーザのユーザID
   * @param apiKey 利用ユーザのAPIキー
   * @param secretKey 利用ユーザのシークレットキー
   * @return JNLPファイルの内容
   */
  def getJnlp(
    datasetId: String,
    userId: String,
    apiKey: String,
    secretKey: String
  ): String = {
    val encriptedApiKey = encript(datasetId, apiKey)
    val encriptedSecretKey = encript(datasetId, secretKey)
    val path = Paths.get(AppConf.appDir, "preset", "base.jnlp")
    val parser = ConstructingParser.fromFile(path.toFile, false)
    val doc = parser.document
    val rule = new RuleTransformer(new RewriteRule {
      override def transform(n: Node) = n match {
        case <jar></jar> => {
          // JARファイルのURLを埋め込み
          <jar href={ getJarUrl(datasetId, userId) }></jar>
        }
        case <property></property> => {
          val attrs = n.asInstanceOf[Elem].attributes.asAttrMap
          attrs.get("name") match {
            case Some("jnlp.dsmoq.url") => {
              // URLルートを埋め込み
              <property name="jnlp.dsmoq.url" value={ AppConf.urlRoot }></property>
            }
            case Some("jnlp.dsmoq.user.apiKey") => {
              // 利用ユーザのAPIキーを埋め込み
              <property name="jnlp.dsmoq.user.apiKey" value={ encriptedApiKey }></property>
            }
            case Some("jnlp.dsmoq.user.secretKey") => {
              // 利用ユーザのシークレットキーを埋め込み
              <property name="jnlp.dsmoq.user.secretKey" value={ encriptedSecretKey }></property>
            }
            case Some("jnlp.dsmoq.dataset.id") => {
              // データセットIDを埋め込み
              <property name="jnlp.dsmoq.dataset.id" value={ datasetId }></property>
            }
            case _ => {
              n
            }
          }
        }
        case _ => {
          n
        }
      }
    })
    val transformed = rule(doc.docElem)
    val sw = new StringWriter
    // XML宣言出力のためXML.writeを使用 (node.toStringだとXML宣言を含まない)
    XML.write(sw, transformed, doc.encoding.getOrElse(DEFAULT_JNLP_CHAESAET.name()), true, null)
    sw.toString
  }

  /**
   * 指定された文字列を暗号化する。
   *
   * @param key 暗号化に用いる鍵
   * @param value 暗号化する文字列
   * @return 暗号化された文字列
   */
  def encript(key: String, value: String): String = {
    val spec = new DESedeKeySpec(key.getBytes)
    val skf = SecretKeyFactory.getInstance("DESede")
    val sk = skf.generateSecret(spec)
    val cipher = Cipher.getInstance("DESede")
    cipher.init(Cipher.ENCRYPT_MODE, sk)
    val bytes = cipher.doFinal(value.getBytes)
    DatatypeConverter.printBase64Binary(bytes)
  }
}
