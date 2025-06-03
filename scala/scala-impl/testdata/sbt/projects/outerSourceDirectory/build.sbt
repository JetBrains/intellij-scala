ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
.settings(
  Compile / sourceDirectory := baseDirectory.value / "foo" / "src" / "main",
  Compile / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "foo" / "src" )
)
