resolvers += "central" at "http://repo1.maven.org/maven2/"

resolvers += "Sonatype releases" at "http://oss.sonatype.org/content/repositories/releases/"

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "mvnrepository" at "http://mvnrepository.com/artifact/"

libraryDependencies += "org.postgresql" % "postgresql" % "9.3-1100-jdbc41"

addSbtPlugin("org.scalatra.sbt" % "scalatra-sbt" % "0.3.2")

addSbtPlugin("org.scalikejdbc" %% "scalikejdbc-mapper-generator" % "1.7.4")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.7.0-SNAPSHOT")

