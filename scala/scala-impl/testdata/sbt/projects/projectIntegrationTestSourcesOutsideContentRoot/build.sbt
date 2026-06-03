//NOTE: this is a reduced version of example project from SCL-23577
import IntegrationTestProjectPlugin.integrationTestProjectSuffix
import sbt.Project.projectToRef

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(name := "root")
  .aggregate(
    Seq(subProject).flatMap { p =>
      Seq(projectToRef(p), LocalProject(s"${p.id}$integrationTestProjectSuffix"))
    } *
  )

lazy val subProject = (project in file("subProject"))
