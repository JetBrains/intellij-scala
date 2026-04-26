object Dependencies {
  import sbt._

  val projectScalaVersion = "2.12.15"
  val scalaTestVersion = "3.2.9"
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
}

object TestUsingXSource3 {
  //since sbt 1.6 it supports -Xsource:3 compiler flag implicitely (see SCL-20505)
  //here we test that `*` syntax in imports is supported (only availalbe in -Xsource:3)
  import sbt.*

  val dummyVersion = "1.2.3"
  val dummyLibrary = "org.example" %% "dummy" % dummyVersion % Test
}