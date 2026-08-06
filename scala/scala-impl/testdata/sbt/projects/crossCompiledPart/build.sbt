lazy val root = project.in(file("."))
  .settings(crossScalaVersions := Nil)
  .aggregate(subproject1, subproject2)

lazy val subproject1 = project.in(file("subproject1")).settings(
  scalaVersion := "3.0.2",
  crossScalaVersions := List("3.0.2", "2.13.14"))

lazy val subproject2 = project.in(file("subproject2")).settings(
  scalaVersion := "3.0.2",
  crossScalaVersions := List("3.0.2"))
