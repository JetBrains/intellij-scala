lazy val root = project.in(file("."))
  .aggregate(base, impl, main)
  .settings(
    name := "project-metadata"
  )

lazy val base = project.in(file("base"))
  .settings(
    autoScalaLibrary := false,
    crossPaths := false
  )

lazy val impl = project.in(file("impl"))
  .dependsOn(base)
  .settings(
    scalaVersion := "3.7.4"
  )

lazy val main = project.in(file("main"))
  .dependsOn(impl)
  .settings(
    scalaVersion := "3.7.4"
  )
