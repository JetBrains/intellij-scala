ThisBuild / scalaVersion := "2.13.11"
name := "nonSourceConfigurationsWithNestedProjectDependencies"

lazy val proj0 = project in file("./proj0")

lazy val proj1 = (project in file("./proj1"))
  .dependsOn(proj0 % "test")

lazy val proj2 = (project in file("./proj2"))
  .dependsOn(proj1 % "provided->test")

lazy val proj3 = (project in file("./proj3"))
  .dependsOn(proj2 % "compile->provided")

lazy val root = (project in file("."))
  .dependsOn(proj2 % "test->compile")