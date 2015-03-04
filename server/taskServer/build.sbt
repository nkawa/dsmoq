// sbt assemblyの暫定設定(Standalone deployment用)
import AssemblyKeys._

scalikejdbcSettings

assemblySettings

assemblyAdditionalSettings

scalaVersion := "2.11.4"

mainClass in assembly := Some("dsmoq.taskServer.Main")

test in assembly := {}

jarName := "taskServer.jar"