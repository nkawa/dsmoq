import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

import scala.language.reflectiveCalls

import org.scalatra.LifeCycle

import dsmoq.controllers.ApiController
import dsmoq.controllers.FileController
import dsmoq.controllers.GoogleOAuthController
import dsmoq.controllers.ImageController
import dsmoq.controllers.MockController
import dsmoq.controllers.ResourceController
import dsmoq.controllers.SessionsController
import javax.servlet.ServletContext
import scalikejdbc.Closable
import scalikejdbc.GlobalSettings
import scalikejdbc.LoggingSQLAndTimeSettings
import scalikejdbc.config.DBs

class ScalatraBootstrap extends LifeCycle {
  /**
   * UTF-8 エンコーディングなプロパティファイルを取り扱うためのResourceBundle.Control
   */
  private val UTF8_ENCODING_CONTROL = new ResourceBundle.Control {
    override def newBundle(
      baseName: String,
      locale: Locale,
      format: String,
      loader: ClassLoader,
      reload: Boolean): ResourceBundle = {
      val bundleName = toBundleName(baseName, locale)
      val resourceName = toResourceName(bundleName, "properties")
      use(loader.getResourceAsStream(resourceName)) { is =>
        use(new InputStreamReader(is, "UTF-8")) { isr =>
          use(new BufferedReader(isr)) { reader =>
            new PropertyResourceBundle(reader)
          }
        }
      }
    }

    private def use[T1 <: Closable, T2](resource: T1)(action: T1 => T2): T2 = {
      try {
        action(resource)
      } finally {
        resource.close
      }
    }
  }

  override def init(context: ServletContext) {
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      singleLineMode = true,
      logLevel = 'DEBUG
    )
    DBs.setup()

    val resource = ResourceBundle.getBundle("message", UTF8_ENCODING_CONTROL)

    context.mount(new ResourceController, "/*")
    context.mount(new SessionsController, "/sessions/*")
    context.mount(new ApiController(resource), "/api/*")
    context.mount(new GoogleOAuthController(resource), "/google_oauth/*")
    context.mount(new ImageController(resource), "/images/*")
    context.mount(new FileController(resource), "/files/*")

    // mock
    context.mount(new MockController, "/mock")

    System.setProperty(org.scalatra.EnvironmentKey, "development")
  }

  override def destroy(context: ServletContext) {
    DBs.close()
  }
}
