import BuildSourcesClass._
import java.lang._

name := "trailing-comma-test-project-3"

version := "0.1"

scalaVersion := "2.12.2"

scalacOptions ++= Seq(
  "1",
  "TrailingCommaMarker"
)
