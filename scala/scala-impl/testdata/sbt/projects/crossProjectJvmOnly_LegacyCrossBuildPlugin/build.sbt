name := "root"
ThisBuild / scalaVersion := "2.13.14"

lazy val p1 = crossProject.in(file("p1"))
lazy val p1JVM = p1.jvm
//lazy val p1JS = p1.js