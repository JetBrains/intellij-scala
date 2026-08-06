scalaVersion := "2.13.14"

lazy val simpleTwoBuilds = (project in file("."))
  .aggregate(c2)

lazy val c2 = RootProject(file("c2"))
