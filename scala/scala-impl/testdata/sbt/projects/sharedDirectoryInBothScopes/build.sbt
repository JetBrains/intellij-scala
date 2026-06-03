ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    Test / unmanagedSourceDirectories := List((ThisBuild/ baseDirectory).value / "test" / "common"),
    Compile / unmanagedResourceDirectories := (Test / unmanagedSourceDirectories).value,
  )

lazy val foo = (project in file("foo"))
  .settings(
    Test / unmanagedSourceDirectories := List((ThisBuild/ baseDirectory).value / "test" / "common"),
  )