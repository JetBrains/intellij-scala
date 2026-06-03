ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    // The source directory is the same as base directory
    Compile / sourceDirectory := baseDirectory.value,
    // The same source directories in different scopes
    Compile / unmanagedSourceDirectories += baseDirectory.value/ "dummy",
    Test / unmanagedSourceDirectories += baseDirectory.value/ "dummy",
    // The unmanaged source directory in root is the same as source directory in Compile scope in foo
    Test / unmanagedSourceDirectories += baseDirectory.value / "foo" /"src"/ "main",
  )


lazy val foo = (project in file("foo"))
