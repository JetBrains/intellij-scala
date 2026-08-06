ThisBuild / version := "1.2.3"

// in sbt 2.0 this should be same as ThisBuld / scalaVersion
scalaVersion := "3.3.3"

onLoad := {
  println("[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038")
  onLoad.value
}

lazy val root = (project in file(".")).aggregate(subProject1, subProject2)
lazy val subProject1 = project.in(file("subProject1"))
lazy val subProject2 = project.in(file("subProject2")).settings(scalaVersion := "3.6.2")