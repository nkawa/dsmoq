import com.etsy.sbt.checkstyle.CheckstylePlugin.autoImport._
import de.johoop.findbugs4sbt.FindBugs._
import org.scalastyle.sbt.ScalastylePlugin._
import sbt.Keys._
import sbt._

object DsmoqSdkBuild extends Build {
  lazy val scalastyleSettings: Seq[Def.Setting[File]] = {
    val ssc: Def.Initialize[File] = Def.setting(file("../server/project/scalastyle-config.xml"))
    Seq(
      scalastyleConfig in Compile := ssc.value,
      scalastyleConfig in Test := ssc.value
    )
  }

  val Organization = "dsmoq"
  val Version = "1.0.0"
  val ScalaVersion = "2.11.4"

  val JavaDoc = config("genjavadoc") extend Compile

  val javadocSettings = inConfig(JavaDoc)(Defaults.configSettings) ++ Seq(
    libraryDependencies += compilerPlugin("com.typesafe.genjavadoc" %%
      "genjavadoc-plugin" % "0.8" cross CrossVersion.full),
    scalacOptions <+= target map (t => "-P:genjavadoc:out=" + (t / "java")),
    packageDoc in Compile <<= packageDoc in JavaDoc,
    sources in JavaDoc <<=
      (target, compile in Compile, sources in Compile) map ((t, c, s) =>
        (t / "java" ** "*.java").get ++ s.filter(_.getName.endsWith(".java"))),
    javacOptions in JavaDoc := Seq("-encoding", "UTF-8", "-charset", "UTF-8", "-docencoding", "UTF-8"),
    artifactName in packageDoc in JavaDoc :=
      ((sv, mod, art) =>
        "" + mod.name + "_" + sv.binary + "-" + mod.revision + "-javadoc.jar")
  )

  lazy val sdk = (project in file("."))
    .settings(Defaults.coreDefaultSettings)
    .settings(
      organization := Organization,
      name := "dsmoq-sdk",
      version := Version,
      resolvers += Classpaths.typesafeReleases,
      scalaVersion := ScalaVersion,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      javacOptions ++= Seq("-encoding", "UTF-8"),
      libraryDependencies ++= Seq(
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4",
        "com.novocode" % "junit-interface" % "0.11",
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.scala-logging" % "scala-logging_2.11" % "3.1.0" % "compile",
        "joda-time" % "joda-time" % "2.7",
        "junit" % "junit" % "4.12",
        "org.apache.httpcomponents" % "httpclient" % "4.3.6",
        "org.apache.httpcomponents" % "httpmime" % "4.3.6",
        "org.joda" % "joda-convert" % "1.7",
        "org.json4s" %% "json4s-jackson" % "3.2.10"
      ),
      parallelExecution in Test := false
    )
    .settings(scalastyleSettings)
    .settings(findbugsSettings)
    .settings(
      findbugsReportPath := Some(target.value / "findbugs-report.xml"),
      checkstyleConfigLocation := CheckstyleConfigLocation.File("project/checkstyle_checks.xml")
    )
    .settings(javadocSettings)
}
