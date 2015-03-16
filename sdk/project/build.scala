import sbt._
import Keys._

object DsmoqSdkBuild extends Build {
  val Organization = "dsmoq"
  val Name = "dsmoq"
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
    javacOptions in JavaDoc := Seq("-charset", "UTF-8", "-docencoding", "UTF-8"),
    artifactName in packageDoc in JavaDoc :=
      ((sv, mod, art) =>
        "" + mod.name + "_" + sv.binary + "-" + mod.revision + "-javadoc.jar")
  )


  lazy val project = Project(
    "dsmoq-sdk",
    file("."),
    settings = Defaults.coreDefaultSettings ++ javadocSettings ++ Seq(
      organization := Organization,
      name := "dsmoq-sdk",
      version := Version,
      resolvers += Classpaths.typesafeReleases,
      scalaVersion := ScalaVersion,
      libraryDependencies ++= Seq(
        "org.apache.httpcomponents" % "httpclient" % "4.3.6",
        "org.apache.httpcomponents" % "httpmime" % "4.3.6",
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4",
        "junit" % "junit" % "4.12",
        "joda-time" % "joda-time" % "2.7"
      )
    )
  )
}
