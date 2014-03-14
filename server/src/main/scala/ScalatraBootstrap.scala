import com.constructiveproof.example._
import org.scalatra._
import javax.servlet.ServletContext
import scalikejdbc.config.DBs

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    DBs.setup()
    context.mount(new ResourceController, "/*")
    context.mount(new SessionsController, "/sessions/*")
    context.mount(new ServerApiController, "/api/*")

    // mock
    context.mount(new MockController, "/mock")
  }

  override def destroy(context: ServletContext) {
    DBs.close()
  }
}
