ThisBuild / scalaVersion := "2.13.17"

lazy val root = (project in file("."))
  .settings(
    name := "root",
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.8.9" % "test",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )
