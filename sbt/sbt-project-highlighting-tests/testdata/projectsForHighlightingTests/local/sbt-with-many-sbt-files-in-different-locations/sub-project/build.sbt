import Dependencies._

name := "sub-project"
version := "0.3.0"

Compile / run / mainClass := Some("src.main.scala.Main")

libraryDependencies ++= Seq(
  scalaTest, //notice trailing comma
)

def dummyMethodToTriggerInspection() {}
//unresolvedReference