ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))

lazy val project1 = (project in file("dir1"))
  .settings(name := "dir/mo\\d")

lazy val project2 = (project in file("dir2"))
.settings(name := "dir/mo\\d")
