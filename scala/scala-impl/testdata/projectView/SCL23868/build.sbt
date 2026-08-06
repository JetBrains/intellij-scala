ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    Compile / unmanagedSourceDirectories := List((ThisBuild / baseDirectory).value / "foo" /"custom" )
  )
lazy val foo = (project in file ("foo"))