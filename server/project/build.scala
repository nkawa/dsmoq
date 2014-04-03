import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import org.scalatra.sbt.DistPlugin.Dist
import com.earldouglas.xsbtwebplugin.PluginKeys._
import com.earldouglas.xsbtwebplugin.WebPlugin.container

object DsmoqBuild extends Build {
  val Organization = "dsmoq"
  val Name = "dsmoq"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.2"

  lazy val project = Project (
    "dsmoq-server",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      //port in container.Configuration := 8080,
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.10.v20130312" % "compile;container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test",
        "org.postgresql" % "postgresql" % "9.3-1101-jdbc41",
        "org.scalikejdbc" %% "scalikejdbc" % "1.7.4",
        "org.scalikejdbc" %% "scalikejdbc-interpolation" % "1.7.4",
        "org.scalikejdbc" %% "scalikejdbc-config" % "1.7.4",
        "org.scalikejdbc" %% "scalikejdbc-test" % "1.7.4" % "test",
        "org.scalatra" %% "scalatra-json" % "2.2.2",
        "org.json4s" %% "json4s-jackson" % "3.2.6",
        "com.google.api-client" % "google-api-client" % "1.18.0-rc",
        "com.google.http-client" % "google-http-client-jackson" % "1.18.0-rc",
        "com.google.apis" % "google-api-services-oauth2" % "v2-rev66-1.18.0-rc"
      )
    )
  )
}
