import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalaVersion := "3.7.1"

lazy val sharedSourcesEvalTest = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(
    name := "sharedSourcesEvalTest"
  )

lazy val sharedSourcesEvalTestJS = sharedSourcesEvalTest.js
lazy val sharedSourcesEvalTestJVM = sharedSourcesEvalTest.jvm
