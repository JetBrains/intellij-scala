ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    Compile / unmanagedSourceDirectories += baseDirectory.value / ".." / "externalSources"
  )
lazy val foo = (project in file("foo"))