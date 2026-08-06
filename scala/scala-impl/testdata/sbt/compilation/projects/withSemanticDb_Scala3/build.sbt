ThisBuild / scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .settings(
    name := "my-test-withSemanticDb_Scala3",
    semanticdbEnabled := true
  )