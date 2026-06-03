ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.6"

lazy val root = (project in file("."))
  .settings(
    name := "withProvidedAndRuntimeDependencies",
    //NOTE: ensure dependencies size is not big, in order tests do not take long to download them
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % "1.21" notTransitive(),
      "org.apache.commons" % "commons-math" % "2.2" % Provided notTransitive(),
      "org.apache.commons" % "commons-text" % "1.9" % Runtime notTransitive(),
    ),
    publishMavenStyle := false //just to avoid some warnings on sbt import
  )