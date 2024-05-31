ThisBuild / scalaVersion := "2.13.6"

lazy val packagePrefix = project.in(file("."))
  .settings(
    idePackagePrefix := Some("com.example")
  )
