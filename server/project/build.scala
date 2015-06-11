import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import org.scalatra.sbt.DistPlugin.Dist
import com.earldouglas.xsbtwebplugin.PluginKeys._
import com.earldouglas.xsbtwebplugin.WebPlugin.container
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin.MergeStrategy

object DsmoqBuild extends Build {
  lazy val assemblyAdditionalSettings = Seq(
    mergeStrategy in assembly ~= { (old) => {
      case "application.conf" => MergeStrategy.concat
      case "mime.types" => MergeStrategy.discard
      case x => old(x)
    }
    }
  )

  val Organization = "dsmoq"
  val Name = "dsmoq"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.4"
  val ScalatraVersion = "2.3.0"

  lazy val dsmoq = Project (
    "dsmoq-server",
    file("."),
    settings = Defaults.coreDefaultSettings ++ ScalatraPlugin.scalatraSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      //port in container.Configuration := 8080,
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "com.amazonaws" % "aws-java-sdk" % "1.9.4",
        "com.google.api-client" % "google-api-client" % "1.18.0-rc" excludeAll( ExclusionRule(organization = "org.apache.httpcomponents") ),
        "com.google.apis" % "google-api-services-oauth2" % "v2-rev66-1.18.0-rc" excludeAll( ExclusionRule(organization = "org.apache.httpcomponents") ),
        "com.google.http-client" % "google-http-client-jackson" % "1.18.0-rc" excludeAll( ExclusionRule(organization = "org.apache.httpcomponents") ),
        "commons-io" % "commons-io" % "2.4",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.1.v20140609" % "compile;container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "org.postgresql" % "postgresql" % "9.3-1101-jdbc41",
        "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
        "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
        "com.github.tototoshi" %% "scala-csv" % "1.1.2",
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "org.scalatest" %% "scalatest" % "2.2.1" % "test",
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalikejdbc" %% "scalikejdbc" % "2.2.3",
        "org.scalikejdbc" %% "scalikejdbc-config" % "2.2.3",
        "org.scalikejdbc" %% "scalikejdbc-interpolation" % "2.2.3",
        "org.scalikejdbc" %% "scalikejdbc-test" % "2.2.3" % "test"
      ),
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      fork in Test := true
    )
  )
  
  lazy val initGroupMember = Project(
    id = "initGroupMember",
    base = file("initGroupMember"),
    settings = Defaults.coreDefaultSettings ++ Seq(
      organization := Organization,
      name := "initGroupMember",
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
        "com.github.tototoshi" %% "scala-csv" % "1.1.0"
      )
    )
  ).dependsOn(dsmoq)

  lazy val taskServer = Project(
    id = "taskServer",
    base = file("taskServer"),
    settings = Defaults.coreDefaultSettings ++ Seq(
      organization := Organization,
      name := "taskServer",
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
        "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "0.11",
        "com.typesafe.akka" % "akka-testkit_2.11" % "2.3.7"
      ),
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
    )
  ).dependsOn(dsmoq)

  lazy val statisticsBatch = Project(
    id = "statisticsBatch",
    base = file("statisticsBatch"),
    settings = Defaults.coreDefaultSettings ++ Seq(
      organization := Organization,
      name := "statisticsBatch",
      version := Version,
      scalaVersion := ScalaVersion
    )
  ).dependsOn(dsmoq)
  
  lazy val apiKeyTool = Project(
    id = "apiKeyTool",
    base = file("apiKeyTool"),
    settings = Defaults.coreDefaultSettings ++ Seq(
      organization := Organization,
      name := "apiKeyTool",
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-nop" % "1.7.7"
      )
    )
  ).dependsOn(dsmoq)

  lazy val tagManager = Project(
    id = "tagManager",
    base = file("tagManager"),
    settings = Defaults.coreDefaultSettings ++ Seq(
      organization := Organization,
      name := "tagManager",
      version := Version,
      scalaVersion := ScalaVersion
    )
  ).dependsOn(dsmoq)
}
