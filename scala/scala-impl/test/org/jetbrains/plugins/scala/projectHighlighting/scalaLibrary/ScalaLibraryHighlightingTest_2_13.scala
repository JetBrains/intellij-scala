package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion

class ScalaLibraryHighlightingTest_2_13 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2762, 2769), // Cannot resolve symbol subargs
      (2995, 3002), //Cannot resolve symbol subtype
    ),
    //SCL-20549
    "scala/runtime/ClassValueCompat.scala" -> Set(
      (503, 522) //"Cannot resolve symbol classValueAvailable"
    ),
    //SCL-20548
    "scala/collection/StrictOptimizedSortedMapOps.scala" -> Set(
      (1809, 1810) // - Cannot resolve symbol +
    )
  )
}
