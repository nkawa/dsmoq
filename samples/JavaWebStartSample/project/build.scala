import sbt.Keys._
import sbt._

object JavaWebStartSampleBuild extends Build {

  val Organization = "dsmoq"
  val Version = "1.0.0"
  val ScalaVersion = "2.11.4"

  lazy val sample = (project in file("."))
    .settings(Defaults.coreDefaultSettings)
    .settings(
      organization := Organization,
      name := "dsmoq-jws-sample",
      version := Version,
      scalaVersion := ScalaVersion,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      javacOptions ++= Seq("-encoding", "UTF-8"),
      libraryDependencies ++= Seq(
        "dsmoq" %% "dsmoq-sdk" % "1.0.0"
      )
    )
}
