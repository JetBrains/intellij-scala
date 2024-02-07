ThisBuild / scalaVersion := "2.13.6"

lazy val mod1 = project.in(file("mod1"))
  .settings(name := "ro/t")

lazy val mod2 = project.in(file("mod2"))
  .settings(name := "ro_t")

lazy val c1 = project.in(file("."))

