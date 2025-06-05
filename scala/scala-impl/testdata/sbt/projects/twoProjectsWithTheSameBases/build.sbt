ThisBuild / scalaVersion := "3.0.2"

lazy val root = (project in file("."))

lazy val foo = (project in file("foo"))

lazy val dummy = (project in file("foo"))
  .settings(
    Compile / sourceDirectory := (ThisBuild / baseDirectory).value / "dummy" / "src" / "main",
    Test / sourceDirectory := (ThisBuild / baseDirectory).value / "dummy"/ "src" / "test",
    target := (ThisBuild / baseDirectory).value / "dummy" / "target"
  )