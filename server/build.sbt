// sbt assemblyの暫定設定(Standalone deployment用)
import AssemblyKeys._

scalikejdbcSettings

assemblySettings

scalaVersion := "2.10.3"

mainClass in assembly := Some("dsmoq.JettyLauncher")

test in assembly := {}

jarName := "dsmoq.jar"