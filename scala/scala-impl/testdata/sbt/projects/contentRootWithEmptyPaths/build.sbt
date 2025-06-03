ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
.settings(
  Compile / unmanagedSourceDirectories := Nil,
  Compile / unmanagedResourceDirectories := Nil,
)
