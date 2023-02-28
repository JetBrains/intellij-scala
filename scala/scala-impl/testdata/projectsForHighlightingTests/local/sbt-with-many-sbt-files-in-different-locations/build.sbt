import Dependencies._

ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := projectScalaVersion

lazy val root = (project in file("."))
  .settings(commonSettings, name := "root-project")
  .aggregate(subProject)
  .dependsOn(subProjectSeparate)

lazy val subProject = (project in file("sub-project")).settings(commonSettings)

lazy val commonSettings = Seq(
  scalacOptions += "-deprecation", //notice trailing comma
)


lazy val subProjectSeparateFile = file("sub-project-separate")
//lazy val subProjectSeparateBuildRef = BuildRef(subProjectSeparateFile.toURI)
lazy val subProjectSeparate = ProjectRef(subProjectSeparateFile, "subProjectSeparateRoot")



def dummyMethodToTriggerInspection() {}
//unresolvedReference
