ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))

lazy val foo = (project in file("foo"))
  .settings(
    Compile / bspEnabled := false
  )

lazy val bar = (project in file("bar"))
  .settings(
    Runtime / bspEnabled := false
  )
