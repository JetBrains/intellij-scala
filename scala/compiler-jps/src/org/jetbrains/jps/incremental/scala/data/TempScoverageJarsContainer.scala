package org.jetbrains.jps.incremental.scala.data

import java.io.File

object TempScoverageJarsContainer {

  val jars: Seq[File] = {
    val scoverageBase = new File("C:\\Users\\dmitrii.naumenko\\AppData\\Local\\Coursier\\cache\\v1\\https\\repo1.maven.org\\maven2\\org\\scoverage")
    Seq(
      new File(scoverageBase, "scalac-scoverage-plugin_2.12\\1.3.1\\scalac-scoverage-plugin_2.12-1.3.1.jar"),
      new File(scoverageBase, "scalac-scoverage-runtime_2.12\\1.3.1\\scalac-scoverage-runtime_2.12-1.3.1.jar"),
    )
  }
}
