import Dependencies._

ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := projectScalaVersion

lazy val root = (project in file("."))
  .settings(commonSettings, name := "root-project")
  .aggregate(subProject)

lazy val subProject = (project in file("sub-project")).settings(commonSettings)

lazy val commonSettings = Seq(
  scalacOptions += "-deprecation", //notice trailing comma
)


def dummyMethodToTriggerInspection() {}
//unresolvedReference
