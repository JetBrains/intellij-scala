import scala.collection.Seq

ThisBuild / scalaVersion := "2.11.12"

lazy val root = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure).in(file("."))
  .settings(
    name := "root"
  )

