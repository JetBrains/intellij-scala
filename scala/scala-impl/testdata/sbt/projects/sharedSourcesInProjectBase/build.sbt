import sbt.file

ThisBuild / scalaVersion := "2.13.16"

val sharedDirectory = file("src/main/buzz")
lazy val shared = project.in (sharedDirectory)
  .settings (
    Compile / scalaSource := sharedDirectory
  )

lazy val foo = project.in(sharedDirectory/"foo")
  .settings (
    Compile / scalaSource := sharedDirectory
  )

lazy val root = project.in (file ("."))
  .dependsOn(shared)

lazy val dummy = project.in (file ("dummy"))
  .dependsOn(foo)
