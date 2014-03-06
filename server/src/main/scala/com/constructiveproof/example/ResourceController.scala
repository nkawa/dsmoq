package com.constructiveproof.example

import org.scalatra._
import com.constructiveproof.example.facade.LoginFacade
import org.scalatra.json.JacksonJsonSupport
import org.json4s.{DefaultFormats, Formats}

class ResourceController extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  object Ext{
    val Js = """.*\.js$""".r
    val Json = """.*\.json$""".r
    val Css = """.*\.css$""".r
    val Html = """.*\.html$""".r
    val Woff = """.*\.woff""".r
    val Ttf =  """.*\.ttf""".r
    val Otf =  """.*\.otf""".r
    val Eot =  """.*\.eot""".r
    val Jpeg = """.*\.jpe?g$""".r
    val Png = """.*\.png$""".r
    val Gif = """.*\.gif$""".r
    val Zip = """.*\.zip""".r
    val Txt = """.*\.txt""".r
    val Csv = """.*\.csv""".r
  }

  def resource(filename:String) = new java.io.File(
    "../client/www/" + filename
    // servletContext.getResource("filename").getFile
  )

  get ("/*") {
    contentType="text/html"
    resource("index.html")
  }

  // JSON API
  get ("/profile") {
    contentType = formats("json")
    val data = LoginFacade.getLoginInfo
    data
  }

  // JSON API
  post("/signin") {
    // FIXME parameter check & Authentication
    val id = params("id")
    val password = params("password")
    redirect("/")
  }

  // JSON API
  post("/signout") {
    redirect("/")
  }

  get("/resources/(.*)$".r) {
    returnResource(params("captures"))
  }

  post("/resources/(.*)$".r) {
    returnResource(params("captures"))
  }

  def returnResource(filename: String) = {
    contentType = filename match{
      case Ext.Js() => "application/javascript"
      case Ext.Json() => "application/json"
      case Ext.Css() => "text/css"
      case Ext.Html() => "text/html"
      case Ext.Ttf() => "application/x-font-ttf"
      case Ext.Otf() => "application/x-font-opentype"
      case Ext.Woff() => "application/font-woff"
      case Ext.Eot() => "application/vnd.ms-fontobject"
      case Ext.Jpeg() => "image/jpeg"
      case Ext.Png() => "image/png"
      case Ext.Gif() => "image/gif"
      case Ext.Zip() => "application/zip"
      case Ext.Txt() => "text/plain"
      case Ext.Csv() => "text/csv"
      case _ => throw new Exception("hoge-")
    }
    resource(filename)
  }
}


