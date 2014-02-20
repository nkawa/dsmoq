package com.constructiveproof.example

import org.scalatra._

class ResourceController extends ScalatraServlet {
  object Ext{
    val Js = """.*\.js$""".r
    val Css = """.*\.css$""".r
    val Html = """.*\.html$""".r
    val Woff = """.*\.woff""".r
    val Ttf =  """.*\.ttf""".r
    val Otf =  """.*\.otf""".r
    val Eot =  """.*\.eot""".r
  }

  def resource(filename:String) = new java.io.File(
    "../client/www/" + filename
    // servletContext.getResource("filename").getFile
  )

  get ("/*") {
    contentType="text/html"
    resource("index.html")
  }

  get("/resources/(.*)$".r) {
    val filename = params("captures")
    contentType = filename match{
      case Ext.Js() => "application/javascript"
      case Ext.Css() => "text/css"
      case Ext.Html() => "text/html"
      case Ext.Woff() => ""
      case Ext.Ttf() => "application/x-font-ttf"
      case Ext.Otf() => "application/x-font-opentype"
      case Ext.Woff() => "application/font-woff"
      case Ext.Eot() => "application/vnd.ms-fontobject"
      case _ => throw new Exception("hoge-")
    }
    resource(filename)
  }
}


