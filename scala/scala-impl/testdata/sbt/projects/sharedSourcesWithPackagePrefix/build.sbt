ThisBuild / scalaVersion := "2.13.14"

val prefix = idePackagePrefix := Some("com.example")

lazy val foo = project.in(file("foo")).settings(
  prefix,
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala"
)

lazy val bar = project.in(file("bar")).settings(
  prefix,
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala"
)

lazy val sharedSourcesWithPackagePrefix = project.in(file(".")).aggregate(foo, bar).settings(prefix)
