// sbt assemblyの暫定設定(Standalone deployment用)
import AssemblyKeys._
import DsmoqBuild._

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" % "scala-logging_2.11" % "3.1.0" % "compile",
  "org.slf4j" % "slf4j-api" % "1.7.12" % "compile",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "compile"
)

scalikejdbcSettings

assemblySettings

assemblyAdditionalSettings

scalaVersion := "2.11.4"

mainClass in assembly := Some("dsmoq.JettyLauncher")

test in assembly := {}

jarName := "dsmoq.jar"