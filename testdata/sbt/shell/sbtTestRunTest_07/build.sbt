name := "sbtTestRunTest_07"

scalaVersion := "2.11.8"

val sharedSettings = Seq(
  scalaVersion := "2.11.8",
  organization := "silly",
  version := "42"
)

lazy val scalaTest = project in file("scalaTest")
lazy val uTest: Project = project.settings(sharedSettings:_*)
lazy val specs2: Project = project.settings(sharedSettings:_*)

//lazy val root = (project in file("."))
//  .aggregate(uTest, specs2)

libraryDependencies in specs2 ++= Seq("org.specs2" %% "specs2-core" % "3.8.9" % "test")
libraryDependencies in uTest += "com.lihaoyi" %% "utest" % "0.4.5" % "test"
testFrameworks in uTest += new TestFramework("utest.runner.Framework")