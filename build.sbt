// Settings file for all the modules.
import xml.Group
import sbt._
import Keys._
import Defaults._

organization := "com.github.swagger-akka-http"

scalaVersion := "2.13.0"

crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.10", scalaVersion.value)

organizationHomepage in ThisBuild := Some(url("https://github.com/swagger-akka-http/swagger-scala-module"))

scalacOptions in ThisBuild ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked")  

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

Global / useGpg := false

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "io.swagger.core.v3" % "swagger-core" % "2.1.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.10.1",
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.29" % Test
)

publishTo := {
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
  else
    Some("Sonatype Nexus Releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
}

credentials in ThisBuild += Credentials (Path.userHome / ".ivy2" / ".credentials")

resolvers in ThisBuild ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("snapshots")
)

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

homepage := Some(new URL("https://github.com/swagger-akka-http/swagger-scala-module"))

parallelExecution in Test := false

startYear := Some(2014)

licenses := Seq(("Apache License 2.0", new URL("http://www.apache.org/licenses/LICENSE-2.0.html")))

releasePublishArtifactsAction := PgpKeys.publishSigned.value

pomExtra := {
  pomExtra.value ++ Group(
    <scm>
      <connection>scm:git:git@github.com:swagger-akka-http/swagger-scala-module.git</connection>
      <developerConnection>scm:git:git@github.com:swagger-akka-http/swagger-scala-module.git</developerConnection>
      <url>https://github.com/swagger-akka-http/swagger-scala-module</url>
    </scm>
      <issueManagement>
        <system>github</system>
        <url>https://github.com/swagger-api/swagger-scala-module/issues</url>
      </issueManagement>
      <developers>
        <developer>
          <id>fehguy</id>
          <name>Tony Tam</name>
          <email>fehguy@gmail.com</email>
        </developer>
        <developer>
          <id>pjfanning</id>
          <name>PJ Fanning</name>
          <url>https://github.com/pjfanning</url>
        </developer>
      </developers>
  )
}
