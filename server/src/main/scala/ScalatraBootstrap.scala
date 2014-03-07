import com.constructiveproof.example._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new ResourceController, "/*")
    context.mount(new SessionsController, "/sessions/*")
    context.mount(new ServerApiController, "/api/*")
  }
}
