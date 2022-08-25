package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.projectHighlighting.ImplicitConversions.tupleToTextRange

class ScalaLibraryHighlightingTest_2_12 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_12

  override protected val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2714, 2721), // Cannot resolve symbol subargs
      (2947, 2954), //Cannot resolve symbol subtype
    ),
  )
}




