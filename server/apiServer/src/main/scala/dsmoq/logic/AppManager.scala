package dsmoq.logic

import java.io.File
import java.nio.file.Paths

import scala.xml.Elem
import scala.xml.Node
import scala.xml.XML
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer

import org.scalatra.servlet.FileItem

import dsmoq.AppConf

object AppManager {
  def upload(appId: String, appVersionId: String, file: FileItem): Unit = {
    val appDir = Paths.get(AppConf.appDir, "upload", appId).toFile
    if (!appDir.exists()) {
      appDir.mkdirs()
    }
    file.write(appDir.toPath.resolve(appVersionId).toFile)
  }

  def download(appId: String, appVersionId: String): File = {
    val fullPath = Paths.get(AppConf.appDir, "upload", appId, appVersionId).toFile
    if (!fullPath.exists()) {
      throw new RuntimeException("file not found")
    }
    new File(fullPath.toString)
  }

  def getJnlpUrl(datasetId: String, appId: String): String = {
    s"${AppConf.appDownloadRoot}${datasetId}/${appId}.jnlp"
  }

  def getJarUrl(datasetId: String, appId: String, appVersionId: String): String = {
    s"${AppConf.appDownloadRoot}${datasetId}/${appId}/${appVersionId}.jar"
  }

  def getJnlp(
    datasetId: String,
    appId: String,
    appVersionId: String,
    apiKey: String,
    secretKey: String
  ): String = {
    val path = Paths.get(AppConf.appDir, "preset", "base.jnlp")
    val xml = XML.loadFile(path.toFile)
    val rule = new RuleTransformer(new RewriteRule {
      override def transform(n: Node) = n match {
        case <jar></jar> => {
          <jar href={ getJarUrl(datasetId, appId, appVersionId) }></jar>
        }
        case <property></property> => {
          val attrs = n.asInstanceOf[Elem].attributes.asAttrMap
          attrs.get("name") match {
            case Some("jnlp.dsmoq.url") => {
              <property name="jnlp.dsmoq.url" value={ AppConf.urlRoot }></property>
            }
            case Some("jnlp.dsmoq.user.apiKey") => {
              <property name="jnlp.dsmoq.user.apiKey" value={ apiKey }></property>
            }
            case Some("jnlp.dsmoq.user.secretKey") => {
              <property name="jnlp.dsmoq.user.secretKey" value={ secretKey }></property>
            }
            case Some("jnlp.dsmoq.dataset.id") => {
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
    rule.transform(xml).toString
  }
}
