package dsmoq.controllers

import org.scalatra.ActionResult
import org.scalatra.NotFound
import org.scalatra.Ok
import org.scalatra.ScalatraServlet

/**
 * /にマッピングされるサーブレットクラス
 * Viewのファイルへのルーティングを提供する。
 */
class ResourceController extends ScalatraServlet {
  /**
   * 取り扱う拡張子
   */
  object Ext {
    val Js = """.*\.js$""".r
    val SourceMap = """.*\.map$""".r
    val Json = """.*\.json$""".r
    val Css = """.*\.css$""".r
    val Html = """.*\.html$""".r
    val Woff = """.*\.woff""".r
    val Ttf = """.*\.ttf""".r
    val Otf = """.*\.otf""".r
    val Eot = """.*\.eot""".r
    val Jpeg = """.*\.jpe?g$""".r
    val Png = """.*\.png$""".r
    val Gif = """.*\.gif$""".r
    val Zip = """.*\.zip""".r
    val Txt = """.*\.txt""".r
    val Csv = """.*\.csv""".r
  }

  /**
   * ファイル名をファイルに変換する。
   *
   * @param filename ファイル名
   * @return 実ファイルオブジェクト
   */
  def resource(filename: String): java.io.File = {
    new java.io.File(
      "../client/www/" + filename
    // servletContext.getResource("filename").getFile
    )
  }

  get("/*") {
    contentType = "text/html"
    resource("index.html")
  }

  get("/resources/(.*)$".r) {
    returnResource(params("captures"))
  }

  post("/resources/(.*)$".r) {
    returnResource(params("captures"))
  }

  delete("/resources/(.*)$".r) {
    returnResource(params("captures"))
  }

  put("/resources/(.*)$".r) {
    returnResource(params("captures"))
  }

  /**
   * ファイル名をActionResultに変換する。
   *
   * @param filename ファイル名
   * @return
   *      Ok(File) ファイル名からContent-Typeが決定できる場合、実ファイルオブジェクト
   *      NotFound ファイル名からContent-Typeが決定できない場合
   */
  def returnResource(filename: String): ActionResult = {
    contextTypeForName(filename) match {
      case None => NotFound()
      case Some(x) => {
        contentType = x
        Ok(resource(filename))
      }
    }
  }

  /**
   * ファイル名からContent-Typeを取得する。
   *
   * @param filename ファイル名
   * @return Content-Typeの文字列
   */
  def contextTypeForName(filename: String): Option[String] = {
    filename match {
      case Ext.Js() => Some("application/javascript")
      case Ext.SourceMap() => Some("application/json")
      case Ext.Json() => Some("application/json")
      case Ext.Css() => Some("text/css")
      case Ext.Html() => Some("text/html")
      case Ext.Ttf() => Some("application/x-font-ttf")
      case Ext.Otf() => Some("application/x-font-opentype")
      case Ext.Woff() => Some("application/font-woff")
      case Ext.Eot() => Some("application/vnd.ms-fontobject")
      case Ext.Jpeg() => Some("image/jpeg")
      case Ext.Png() => Some("image/png")
      case Ext.Gif() => Some("image/gif")
      case Ext.Zip() => Some("application/zip")
      case Ext.Txt() => Some("text/plain")
      case Ext.Csv() => Some("text/csv")
      case _ => None
    }
  }
}
