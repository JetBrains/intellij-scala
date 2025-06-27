ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(name := "root")
  .aggregate(project1, project2, project3)

lazy val project1 = project in file("project1")
lazy val project2 = (project in file("project2")).dependsOn(project1)
lazy val project3 = (project in file("project3")).dependsOn(project2)
