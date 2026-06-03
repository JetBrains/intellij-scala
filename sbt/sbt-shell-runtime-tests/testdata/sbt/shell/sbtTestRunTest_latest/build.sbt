name := "sbtTestRunTest"

scalaVersion := "2.11.8"

val sharedSettings = Seq(
  scalaVersion := "2.11.8",
  organization := "silly",
  version := "42"
)

lazy val scalaTest = project in file("scalaTest")

lazy val uTest = project.settings(
  sharedSettings,
  libraryDependencies += "com.lihaoyi" %% "utest" % "0.4.5" % "test",
  testFrameworks += new TestFramework("utest.runner.Framework")
)
lazy val specs2 = project.settings(
  sharedSettings,
  libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.8.9" % "test")
)

//lazy val root = (project in file("."))
//  .aggregate(uTest, specs2)
