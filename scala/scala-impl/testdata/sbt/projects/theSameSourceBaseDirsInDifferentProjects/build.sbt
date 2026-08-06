ThisBuild / scalaVersion := "2.13.14"

lazy val foo = (project in file("foo"))

lazy val root = (project in file("."))
  .settings(
    Compile / sourceDirectory := baseDirectory.value / "foo" / "src" / "main",
  )
