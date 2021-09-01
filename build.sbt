// Settings file for all the modules.
import xml.Group
import sbt._
import Keys._

organization := "com.github.swagger-akka-http"

ThisBuild / scalaVersion := "2.13.6"

ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.14", "2.13.6", "3.0.2")

ThisBuild / organizationHomepage := Some(url("https://github.com/swagger-akka-http/swagger-scala-module"))

val scalaReleaseVersion = SettingKey[Int]("scalaReleaseVersion")
scalaReleaseVersion := {
  val v = scalaVersion.value
  CrossVersion.partialVersion(v).map(_._1.toInt).getOrElse {
    throw new RuntimeException(s"could not get Scala release version from $v")
  }
}

val scalaMajorVersion = SettingKey[Int]("scalaMajorVersion")
scalaMajorVersion := {
  val v = scalaVersion.value
  CrossVersion.partialVersion(v).map(_._2.toInt).getOrElse {
    throw new RuntimeException(s"could not get Scala major version from $v")
  }
}

ThisBuild / scalacOptions ++= {
  val additionalSettings =
    if (scalaReleaseVersion.value == 2) {
      Seq("-language:existentials")
    } else {
      Seq.empty[String]
    }
  Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-feature") ++ additionalSettings
}

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

Test / publishArtifact := false

pomIncludeRepository := { x => false }

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.32",
  "io.swagger.core.v3" % "swagger-core" % "2.1.10",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.0-rc2",
  "org.scalatest" %% "scalatest" % "3.2.9" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.32" % Test
)

homepage := Some(new URL("https://github.com/swagger-akka-http/swagger-scala-module"))

Test / parallelExecution := false

startYear := Some(2014)

licenses := Seq(("Apache License 2.0", new URL("http://www.apache.org/licenses/LICENSE-2.0.html")))

pomExtra := {
  pomExtra.value ++ Group(
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

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("develop")),
  RefPredicate.Equals(Ref.Branch("1.5")),
  RefPredicate.StartsWith(Ref.Tag("v"))
)

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)
