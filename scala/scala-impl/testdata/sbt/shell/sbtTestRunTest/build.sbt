name := "sbtTestRunTest"

scalaVersion := "2.11.8"

val sharedSettings = Seq(
  scalaVersion := "2.11.8",
  organization := "silly",
  version := "42"
)

lazy val scalaTest = project in file("scalaTest")
lazy val uTest = project.settings(sharedSettings)
lazy val specs2 = project.settings(sharedSettings)

//lazy val root = (project in file("."))
//  .aggregate(uTest, specs2)

libraryDependencies in specs2 ++= Seq("org.specs2" %% "specs2-core" % "3.8.9" % "test")
libraryDependencies in uTest += "com.lihaoyi" %% "utest" % "0.4.5" % "test"
testFrameworks in uTest += new TestFramework("utest.runner.Framework")