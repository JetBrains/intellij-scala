ThisBuild / scalaVersion := "2.13.6"

lazy val c1 = RootProject(file("./c1"))

lazy val multiBuildProjectWithTheSameProjectIdFromIDEAPerspective = project.in(file("."))
  .dependsOn(c1)

