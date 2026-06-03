ThisBuild / scalaVersion := "2.13.14"

lazy val c1 = RootProject(file("./c1"))

lazy val sharedSourcesInsideMultiBuildProject = project.in(file("."))
  .dependsOn(c1)

