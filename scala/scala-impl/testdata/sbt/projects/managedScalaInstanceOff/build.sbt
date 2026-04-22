ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "scalaInstance"
  )

// Note: this is an example of a project where the managed scalaInstance is turned off.
// In the past, a situation like this occurred in the Scala 3 repository (SCL-24321),
// but it is no longer the case, as the modules that previously didn't have a scalaInstance now have one
// (see https://github.com/scala/scala3/commit/41209879c311f754848f53c07ef1575b79512c3).
// However, it can still be reproduced by checking out, e.g., the 3.7.3 tag.
//
// Nevertheless, having no scalaInstance is a valid sbt setup (see https://www.scala-sbt.org/1.x/docs/Configuring-Scala.html#Configuring+Scala+tool+dependencies), so
// a project like this should still be importable in IntelliJ.
lazy val project1 = project.settings(
  autoScalaLibrary := false,
  managedScalaInstance := false,
)
