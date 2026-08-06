ThisBuild / scalaVersion := "2.13.6"

lazy val root = project.in(file("."))
lazy val foo = project.in(file("foo"))
  .settings(
    name := "foo/"
  )
lazy val foo1 = project.in(file("dummy/foo"))
  .settings(
    name := "foo\\"
  )
