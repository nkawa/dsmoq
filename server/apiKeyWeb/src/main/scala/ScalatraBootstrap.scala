import javax.servlet.ServletContext

import dsmoq.apikeyweb._
import org.scalatra._
import scalikejdbc.config.DBs

/**
  * web アプリケーションとしての基本処理。
  * 起動時や終了時に行う設定を記載。
  */
class ScalatraBootstrap extends LifeCycle {
  /**
    * 初期化処理。
    * すべてのwebアクセスをMainServletで処理する。
    *
    * @param context ServletContext
    */
  override def init(context: ServletContext) {
    context.mount(new MainServlet, "/*")

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
