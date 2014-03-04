import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._

object ScentryauthdemoBuild extends Build {
  val Organization = "com.constructiveproof"
  val Name = "ScentryAuthDemo"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.1"

  lazy val project = Project (
    "dsmoq-server",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test",
        "org.postgresql" % "postgresql" % "9.3-1101-jdbc41",
        "org.scalikejdbc" %% "scalikejdbc" % "[1.7,)",
        "org.scalikejdbc" %% "scalikejdbc-interpolation" % "[1.7,)",
        "org.scalikejdbc" %% "scalikejdbc-test" % "[1.7,)" % "test",
        "org.scalatra" %% "scalatra-json" % "2.2.2",
        "org.json4s" %% "json4s-jackson" % "3.2.6"
      )
    )
  )
}
