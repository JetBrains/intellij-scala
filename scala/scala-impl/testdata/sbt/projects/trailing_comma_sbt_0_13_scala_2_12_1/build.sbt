import BuildSourcesClass._
import java.lang._

name := "trailing-comma-test-project-4"

version := "0.1"

scalaVersion := "2.12.1"

scalacOptions ++= Seq(
  "1",
  "TrailingCommaMarker"
)
