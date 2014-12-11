import sbt._
import Keys._

object DsmoqSdkBuild extends Build {
  val Organization = "dsmoq"
  val Name = "dsmoq"
  val Version = "0.1.0-SNAPSHOT"

  lazy val project = Project(
    "dsmoq-sdk",
    file("."),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := "dsmoq-sdk",
      version := Version,
      resolvers += Classpaths.typesafeReleases,
      crossPaths := false,
      autoScalaLibrary := false,
      libraryDependencies ++= Seq(
        "org.projectlombok" % "lombok-maven" % "1.14.8.0",
        "org.apache.httpcomponents" % "httpclient" % "4.3.6",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
      )
    )
  )
}
