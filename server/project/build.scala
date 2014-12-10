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
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.10.v20130312" % "compile;container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
        "org.postgresql" % "postgresql" % "9.3-1101-jdbc41",
        "org.scalikejdbc" %% "scalikejdbc" % "2.1.1",
        "org.scalikejdbc" %% "scalikejdbc-interpolation" % "2.1.1",
        "org.scalikejdbc" %% "scalikejdbc-config" % "2.1.1",
        "org.scalikejdbc" %% "scalikejdbc-test" % "2.1.1" % "test",
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "com.google.api-client" % "google-api-client" % "1.18.0-rc" excludeAll( ExclusionRule(organization = "org.apache.httpcomponents") ),
        "com.google.http-client" % "google-http-client-jackson" % "1.18.0-rc" excludeAll( ExclusionRule(organization = "org.apache.httpcomponents") ),
        "com.google.apis" % "google-api-services-oauth2" % "v2-rev66-1.18.0-rc" excludeAll( ExclusionRule(organization = "org.apache.httpcomponents") ),
        "com.amazonaws" % "aws-java-sdk" % "1.9.4"
      )
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
      libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.1.0"
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
      )
    )
  ).dependsOn(dsmoq)
}
