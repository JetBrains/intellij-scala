lazy val root = project.in(file("."))
  .aggregate(scala2, scala3)
  .settings(
    name := "mixedScala2Scala3",
    version := "SNAPSHOT",
    scalaVersion := "2.13.14"
  )

lazy val scala2 = project.in(file("scala2"))
  .settings(scalaVersion := "2.13.14")

lazy val scala3 = project.in(file("scala3"))
  .settings(scalaVersion := "3.0.2")
