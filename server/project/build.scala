import com.earldouglas.xsbtwebplugin.PluginKeys._
import com.earldouglas.xsbtwebplugin.WebPlugin.container
import com.mojolly.scalate.ScalatePlugin.ScalateKeys._
import com.mojolly.scalate.ScalatePlugin._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import org.scalastyle.sbt.ScalastylePlugin._
import org.scalatra.sbt.DistPlugin.Dist
import org.scalatra.sbt.PluginKeys._
import org.scalatra.sbt._
import sbt.Keys._
import sbt._
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin.MergeStrategy
import scalariform.formatter.preferences._

object DsmoqBuild extends Build {
  lazy val assemblyAdditionalSettings = Seq(
    mergeStrategy in assembly ~= {
      old => {
        case "application.conf" => MergeStrategy.concat
        case "application.conf.sample" => MergeStrategy.discard
        case "mime.types" => MergeStrategy.discard
        case x => old(x)
      }
    }
  )

  lazy val scalariformSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(DanglingCloseParenthesis, Force)
      .setPreference(DoubleIndentClassDeclaration, false)
      .setPreference(FormatXml, false)
  )

  lazy val scalastyleSettings: Seq[Def.Setting[File]] = {
    val ssc: Def.Initialize[File] = Def.setting(file("project/scalastyle-config.xml"))
    Seq(
      scalastyleConfig in Compile := ssc.value,
      scalastyleConfig in Test := ssc.value
    )
  }

  val Organization = "dsmoq"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.4"
  val ScalatraVersion = "2.3.0"

  lazy val dsmoqSettings = Seq(
    organization := Organization,
    version := Version,
    scalaVersion := ScalaVersion,
    resolvers += Classpaths.typesafeReleases,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "2.2.1" % "test"
    ),
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
  ) ++ scalariformSettings ++ scalastyleSettings

  lazy val common = (project in file("common"))
    .settings(Defaults.coreDefaultSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "common",
      libraryDependencies ++= Seq(
        "org.postgresql" % "postgresql" % "9.3-1101-jdbc41",
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalikejdbc" %% "scalikejdbc" % "2.2.3",
        "org.scalikejdbc" %% "scalikejdbc-config" % "2.2.3",
        "org.scalikejdbc" %% "scalikejdbc-interpolation" % "2.2.3",
        "org.scalikejdbc" %% "scalikejdbc-test" % "2.2.3" % "test"
      )
    )

  lazy val apiServer = (project in file("apiServer"))
    .settings(Defaults.coreDefaultSettings)
    .settings(ScalatraPlugin.scalatraSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "apiServer",
      //port in container.Configuration := 8080,
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "ch.qos.logback" % "logback-classic" % "1.1.3" % "compile",
        "com.amazonaws" % "aws-java-sdk" % "1.9.4",
        "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
        "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
        "com.github.tototoshi" %% "scala-csv" % "1.1.2",
        "com.google.api-client" % "google-api-client" % "1.18.0-rc" excludeAll(ExclusionRule(organization = "org.apache.httpcomponents")),
        "com.google.apis" % "google-api-services-oauth2" % "v2-rev66-1.18.0-rc" excludeAll(ExclusionRule(organization = "org.apache.httpcomponents")),
        "com.google.http-client" % "google-http-client-jackson" % "1.18.0-rc" excludeAll(ExclusionRule(organization = "org.apache.httpcomponents")),
        "com.typesafe.scala-logging" % "scala-logging_2.11" % "3.1.0" % "compile",
        "commons-io" % "commons-io" % "2.4",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.1.v20140609" % "compile;container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalikejdbc" %% "scalikejdbc-test" % "2.2.3" % "test",
        "org.slf4j" % "slf4j-api" % "1.7.12" % "compile"
      ),
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      fork in Test := true
    )
    .dependsOn(common)
  
  lazy val initGroupMember = (project in file("initGroupMember"))
    .settings(Defaults.coreDefaultSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "initGroupMember",
      libraryDependencies ++= Seq(
        "com.github.tototoshi" %% "scala-csv" % "1.1.0"
      )
    )
    .dependsOn(common)

  lazy val taskServer = (project in file("taskServer"))
    .settings(Defaults.coreDefaultSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "taskServer",
      libraryDependencies ++= Seq(
        "com.amazonaws" % "aws-java-sdk" % "1.9.4",
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "org.slf4j" % "slf4j-nop" % "1.7.7",
        "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "0.11",
        "com.typesafe.akka" % "akka-testkit_2.11" % "2.3.7"
      ),
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
    )
    .dependsOn(common)

  lazy val statisticsBatch = (project in file("statisticsBatch"))
    .settings(Defaults.coreDefaultSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "statisticsBatch",
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-nop" % "1.7.7"
      )
    )
    .dependsOn(common)
  
  lazy val apiKeyTool = (project in file("apiKeyTool"))
    .settings(Defaults.coreDefaultSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "apiKeyTool",
      libraryDependencies ++= Seq(
        "commons-codec" % "commons-codec" % "1.10",
        "org.slf4j" % "slf4j-nop" % "1.7.7"
      )
    )
    .dependsOn(common)

  lazy val tagManager = (project in file("tagManager"))
    .settings(Defaults.coreDefaultSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "tagManager",
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-nop" % "1.7.7"
      )
    )
    .dependsOn(common)

  lazy val maintenance = (project in file("maintenance"))
    .settings(Defaults.coreDefaultSettings)
    .settings(ScalatraPlugin.scalatraSettings)
    .settings(dsmoqSettings)
    .settings(
      name := "apiKeyWeb",
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.1.5" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.15.v20160210" % "container;compile",
        "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
        "commons-codec" % "commons-codec" % "1.10",
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.scala-logging" % "scala-logging_2.11" % "3.1.0" % "compile"
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile) { base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty, /* default imports should be added here */
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ), /* add extra bindings here */
            Some("templates")
          )
        )
      }
    )
    .dependsOn(common)
}
