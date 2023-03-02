import sbt._

object Dependencies {
  val projectScalaVersion = "2.12.15"
  val scalaTestVersion = "3.2.9"
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
}