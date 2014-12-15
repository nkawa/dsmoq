import sbt._
import Keys._

object DsmoqSdkBuild extends Build {
  val Organization = "dsmoq"
  val Name = "dsmoq"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.4"

  lazy val project = Project(
    "dsmoq-sdk",
    file("."),
    settings = Defaults.coreDefaultSettings ++ Seq(
      organization := Organization,
      name := "dsmoq-sdk",
      version := Version,
      resolvers += Classpaths.typesafeReleases,
      scalaVersion := ScalaVersion,
      libraryDependencies ++= Seq(
        "org.apache.httpcomponents" % "httpclient" % "4.3.6",
        "org.apache.httpcomponents" % "httpmime" % "4.3.6",
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
      )
    )
  )
}
