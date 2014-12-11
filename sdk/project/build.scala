import sbt._
import Keys._

object DsmoqSdkBuild extends Build {
  val Organization = "dsmoq"
  val Name = "dsmoq"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.4"

  lazy val project = Project (
    "dsmoq-sdk",
    file("."),
    settings = Defaults.coreDefaultSettings ++ Seq(
      organization := Organization,
      name := "dsmoq-sdk",
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq()
    )
  )
}
