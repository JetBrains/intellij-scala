ThisBuild / scalaVersion := "2.13.14"

lazy val packagePrefix = project.in(file("."))
  .settings(
    idePackagePrefix := Some("com.example")
  )
