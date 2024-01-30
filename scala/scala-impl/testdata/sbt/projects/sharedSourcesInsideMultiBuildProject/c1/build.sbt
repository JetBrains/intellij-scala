ThisBuild / scalaVersion := "2.13.6"

lazy val foo = project.in(file("foo")).settings(
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala"
)

lazy val bar = project.in(file("bar")).settings(
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala"
)

lazy val c1 = project.in(file("."))

