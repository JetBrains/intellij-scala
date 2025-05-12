ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    Compile / unmanagedSourceDirectories += baseDirectory.value,
  )
