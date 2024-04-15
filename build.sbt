// Settings file for all the modules.
import xml.Group
import sbt._
import Keys._
import sbtghactions.JavaSpec.Distribution.Zulu

organization := "com.github.swagger-akka-http"

ThisBuild / scalaVersion := "2.13.13"

ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.19", "2.13.13", "3.3.3")

ThisBuild / organizationHomepage := Some(url("https://github.com/swagger-akka-http/swagger-scala-module"))

autoAPIMappings := true

apiMappings ++= {
  def mappingsFor(organization: String, names: List[String], location: String, revision: (String) => String = identity): Seq[(File, URL)] =
    for {
      entry: Attributed[File] <- (Compile / fullClasspath).value
      module: ModuleID <- entry.get(moduleID.key)
      if module.organization == organization
      if names.exists(module.name.startsWith)
    } yield entry.data -> url(location.format(revision(module.revision)))

  val mappings: Seq[(File, URL)] =
    mappingsFor("org.scala-lang", List("scala-library"), "https://scala-lang.org/api/%s/") ++
      mappingsFor("io.swagger.core.v3", List("swagger-core-jakarta"), "https://javadoc.io/doc/io.swagger.core.v3/swagger-core/%s/") ++
      mappingsFor(
        "com.fasterxml.jackson.core",
        List("jackson-core"),
        "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-core/%s/"
      ) ++
      mappingsFor(
        "com.fasterxml.jackson.core",
        List("jackson-databind"),
        "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/%s/"
      )

  mappings.toMap
}

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

Test / publishArtifact := false

pomIncludeRepository := { x => false }

Compile / unmanagedSourceDirectories ++= {
  if (scalaReleaseVersion.value > 2) {
    Seq(
      (LocalRootProject / baseDirectory).value / "src" / "main" / "scala-3"
    )
  } else {
    Seq(
      (LocalRootProject / baseDirectory).value / "src" / "main" / "scala-2"
    )
  }
}

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "2.0.13",
  "io.swagger.core.v3" % "swagger-core-jakarta" % "2.2.21",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.2",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.slf4j" % "slf4j-simple" % "2.0.13" % Test
)
libraryDependencies ++= {
  if (scalaReleaseVersion.value == 2) {
    Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  } else {
    Seq(
      "com.github.pjfanning" %% "scala3-reflection" % "1.3.1",
      "org.scala-lang" %% "scala3-staging" % scalaVersion.value
    )
  }
}

homepage := Some(new URL("https://github.com/swagger-akka-http/swagger-scala-module"))

Test / parallelExecution := false

startYear := Some(2014)

licenses := Seq(("Apache License 2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.html")))

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

MetaInfLicenseCopy.settings

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep
    .Sbt(List("coverage", "test", "coverageReport"), name = Some("Scala 2.13 build"), cond = Some("startsWith(matrix.scala, '2.13')")),
  WorkflowStep.Sbt(List("test"), name = Some("Scala build"), cond = Some("!startsWith(matrix.scala, '2.13')"))
)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec(Zulu, "8"))
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("develop")),
  RefPredicate.Equals(Ref.Branch("1.5")),
  RefPredicate.StartsWith(Ref.Tag("v"))
)

/*
ThisBuild / githubWorkflowBuildPostamble := Seq(
  WorkflowStep.Use(
    Public("codecov", "codecov-action", "v2"),
    name = Some("Publish to Codecov.io"),
    params = Map("fail_ci_if_error" -> "true"),
    cond = Some("startsWith(matrix.scala, '2.13')")
  )
)
 */

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
      "CI_SNAPSHOT_RELEASE" -> "+publishSigned"
    )
  )
)
