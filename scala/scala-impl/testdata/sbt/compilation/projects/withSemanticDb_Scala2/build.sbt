ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "my-test-withSemanticDb_Scala2",
    addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.6.0" cross CrossVersion.full)
  )
