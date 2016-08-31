package dsmoq.logic

import java.io.File
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

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
   * @param appVersionId アプリバージョンID
   * @param file アプリのJARファイル
   * @throws IOException 入出力エラーが発生した場合
   */
  def upload(appId: String, appVersionId: String, file: FileItem): Unit = {
    val appDir = Paths.get(AppConf.appDir, "upload", appId).toFile
    if (!appDir.exists()) {
      appDir.mkdirs()
    }
    file.write(appDir.toPath.resolve(appVersionId).toFile)
  }

  /**
   * アプリのJARファイルを取得する。
   *
   * @param appId アプリID
   * @param appVersionId アプリバージョンID
   * @return アプリのJARファイル
   * @throws RuntimeException ファイルが存在しない場合
   */
  def download(appId: String, appVersionId: String): File = {
    val fullPath = Paths.get(AppConf.appDir, "upload", appId, appVersionId).toFile
    if (!fullPath.exists()) {
      throw new RuntimeException("file not found")
    }
    new File(fullPath.toString)
  }

  /**
   * アプリのJNLPファイルのURLを取得する。
   *
   * @param datasetId データセットID
   * @param appId アプリID
   * @param userId ユーザID
   * @return JNLPファイルのURL
   */
  def getJnlpUrl(datasetId: String, appId: String, userId: String): String = {
    s"${AppConf.appDownloadRoot}${userId}/${datasetId}/${appId}.jnlp"
  }

  /**
   * アプリのJARファイルのURLを取得する。
   *
   * @param datasetId データセットID
   * @param appId アプリID
   * @param userId ユーザID
   * @return JARファイルのURL
   */
  def getJarUrl(datasetId: String, appId: String, appVersionId: String, userId: String): String = {
    s"${AppConf.appDownloadRoot}${userId}/${datasetId}/${appId}/${appVersionId}.jar"
  }

  /**
   * アプリのJNLPファイルを取得する。
   *
   * @param datasetId データセットID
   * @param appId アプリID
   * @param appVersionId アプリバージョンID
   * @param userId 利用ユーザのユーザID
   * @param apiKey 利用ユーザのAPIキー
   * @param secretKey 利用ユーザのシークレットキー
   * @return JNLPファイルの内容
   */
  def getJnlp(
    datasetId: String,
    appId: String,
    appVersionId: String,
    userId: String,
    apiKey: String,
    secretKey: String
  ): String = {
    val path = Paths.get(AppConf.appDir, "preset", "base.jnlp")
    val parser = ConstructingParser.fromFile(path.toFile, false)
    val doc = parser.document
    val rule = new RuleTransformer(new RewriteRule {
      override def transform(n: Node) = n match {
        case <jar></jar> => {
          // JARファイルのURLを埋め込み
          <jar href={ getJarUrl(datasetId, appId, appVersionId, userId) }></jar>
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
              <property name="jnlp.dsmoq.user.apiKey" value={ apiKey }></property>
            }
            case Some("jnlp.dsmoq.user.secretKey") => {
              // 利用ユーザのシークレットキーを埋め込み
              <property name="jnlp.dsmoq.user.secretKey" value={ secretKey }></property>
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
}
