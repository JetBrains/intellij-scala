lazy val foo = project.in(file("foo")).settings(
  unmanagedSourceDirectories in Compile += baseDirectory.in(ThisBuild).value / "shared" / "src" / "main" / "scala"
)

lazy val bar = project.in(file("bar")).settings(
  unmanagedSourceDirectories in Compile += baseDirectory.in(ThisBuild).value / "shared" / "src" / "main" / "scala"
)

lazy val sharedSources = project.in(file(".")).aggregate(foo, bar)