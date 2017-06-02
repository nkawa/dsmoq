import javax.servlet.ServletContext

import org.scalatra.LifeCycle
import scalikejdbc.config.DBs
import dsmoq.maintenance.controllers._

/**
 * web アプリケーションとしての基本処理。
 *
 * 起動時や終了時に行う設定を記載。
 */
class ScalatraBootstrap extends LifeCycle {
  /**
   * 初期化処理。
   *
   * @param context ServletContext
   */
  override def init(context: ServletContext) {
    context.mount(new ApiKeyServlet, "/apikey/*")
    context.mount(new UserServlet, "/user/*")
    context.mount(new DatasetServlet, "/dataset/*")
    context.mount(new FileServlet, "/file/*")
    context.mount(new GroupServlet, "/group/*")
    context.mount(new LocalUserServlet, "/localuser/*")

    System.setProperty(org.scalatra.EnvironmentKey, "development")
    DBs.setup()
  }

  /**
   * 終期化処理。
   *
   * @param context ServletContext
   */
  override def destroy(context: ServletContext) {
    DBs.close()
  }
}
