import BuildCommons._

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := s"untitled411_${BuildCommons.myLibraryVersion1}_${myLibraryVersion2}"
  )
