import javax.servlet.ServletContext

import dsmoq.apikeyweb._
import org.scalatra._
import scalikejdbc.config.DBs

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new MainServlet, "/*")

    DBs.setup()
  }

  override def destroy(context: ServletContext) {
    DBs.close()
  }
}
