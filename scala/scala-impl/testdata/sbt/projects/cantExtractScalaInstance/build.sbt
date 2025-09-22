ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "scalaInstance"
  )
  // Note: this is not a standard setup, when we depend on a project without scala instance
  // This is a synthetic exampel to emulate some part of Scala 3 reporsitory (SCL-24321)
  .dependsOn(
    project1,
    project2,
    project3,
  )

// No scalaInstance at all
lazy val project1 = project.settings(
  autoScalaLibrary := false,
  managedScalaInstance := false,
)

// No auto scalaInstance, custom scala instance construction is failing
lazy val project2 = project.settings(
  autoScalaLibrary := false,
  managedScalaInstance := false,

  scalaInstance := {
    val error = "Can't create scala instance"
    System.err.println(error)
    throw new RuntimeException(error)
  }
)


// No auto-scalaInstance,
// manual scalaInstance is constructed indirectly as per
// https://www.scala-sbt.org/1.x/docs/Configuring-Scala.html#Configuring+Scala+tool+dependencies
// (also see sbt.Defaults.scalaInstanceFromUpdate)
lazy val project3 = project.settings(
  autoScalaLibrary := false,
  managedScalaInstance := false,

  // Add the configuration for the dependencies on Scala tool jars
  // You can also use a manually constructed configuration like:
  //   config("scala-tool").hide
  ivyConfigurations += Configurations.ScalaTool,

  // Add the usual dependency on the library as well on the compiler in the
  //  'scala-tool' configuration
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-library" % "2.13.14",
    "org.scala-lang" % "scala-compiler" % "2.13.14" % "scala-tool"
  )
)
