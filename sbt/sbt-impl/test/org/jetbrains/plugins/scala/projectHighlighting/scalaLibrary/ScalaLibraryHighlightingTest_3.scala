package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion

class ScalaLibraryHighlightingTest_3 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  override protected val filesWithProblems: Map[String, Set[TextRange]] = Map()

  override protected def scalaLibraryJarName: String = "scala3-library_3"
}
