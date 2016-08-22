package dsmoq.logic

import java.nio.file.Paths

import org.scalatra.servlet.FileItem

import dsmoq.AppConf

object AppManager {
  def upload(appId: String, appVersionId: String, file: FileItem): Unit = {
    val appDir = Paths.get(AppConf.fileDir, appId).toFile
    if (!appDir.exists()) {
      appDir.mkdirs()
    }
    file.write(appDir.toPath.resolve(appVersionId).toFile)
    // TODO: app_name.jnlp、WEB-INF/web.xml がなければ作成
  }
}
