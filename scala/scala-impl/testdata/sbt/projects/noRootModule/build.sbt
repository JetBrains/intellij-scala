ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    ideSkipProject := true
  )

lazy val fooModule = (project in file("fooModule"))
lazy val dummy = (project in file("dummy"))
