import sbt._
import Keys._

import sbtide.Keys._

object MyBuild extends Build {
  lazy val root = project.in(file(".")).settings(
    ideExcludedDirectories := Seq(
      file("directory-to-exclude-1"),
      file("directory") / "to" / "exclude" / "2"
    )
  )
}