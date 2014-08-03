name := "Runners"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "2.3.11",
  "org.scalatest" % "scalatest_2.11" % "2.2.1",
  "com.lihaoyi" %% "utest" % "0.1.3"
)