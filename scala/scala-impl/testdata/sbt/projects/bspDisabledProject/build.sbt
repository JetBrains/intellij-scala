ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))

lazy val foo = (project in file("foo"))

lazy val dummy = (project in file("dummy"))
  .settings(
    bspEnabled := false
  )
