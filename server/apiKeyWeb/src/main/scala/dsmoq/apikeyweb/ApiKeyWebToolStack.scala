package dsmoq.apikeyweb

import org.scalatra._
import scalate.ScalateSupport
import org.fusesource.scalate.{ TemplateEngine, Binding }
import org.fusesource.scalate.layout.DefaultLayoutStrategy
import javax.servlet.http.HttpServletRequest
import collection.mutable

/**
 * ScalatraServletとScalateSupportのラッパークラス。
 * Scalatraのwebアプリケーションフレームワーク機能を使用できるようにする。
 */
trait ApiKeyWebToolStack extends ScalatraServlet with ScalateSupport {

  /**
   * 404 Not Found時の処理。
   */
  notFound {
    // remove content type in case it was set through an action
    contentType = null
    // Try to render a ScalateTemplate if no route matched
    findTemplate(requestPath) map { path =>
      contentType = "text/html"
      layoutTemplate(path)
    } orElse serveStaticResource() getOrElse resourceNotFound()
  }

}
