lazy val root = project.in(file("."))
  .dependsOn(javaLibrarySubproject)
  .settings(
    name := "nested-source-directories-deduplication",
    scalaVersion := "3.7.4",
    Compile / unmanagedSourceDirectories += (Compile / sourceDirectory).value / "scala" / "nested"
  )

lazy val javaLibrarySubproject = project.in(file("java-library"))
  .settings(
    name := "java-library",
    autoScalaLibrary := false,
    crossPaths := false,
    Compile / unmanagedSourceDirectories += (Compile / sourceDirectory).value / "java" / "nested"
  )
