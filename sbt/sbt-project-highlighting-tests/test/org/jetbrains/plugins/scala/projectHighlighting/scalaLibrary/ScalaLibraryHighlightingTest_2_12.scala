package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

class ScalaLibraryHighlightingTest_2_12 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_12

  override protected lazy val projectJdk: Sdk =
    SmartJDKLoader.createFilteredJdk(LanguageLevel.JDK_17, Seq("java.base", "java.desktop", "java.management"))

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/Predef.scala" -> Set(
      (6399, 6408), // Cannot resolve symbol `package`
    ),
    "scala/collection/CustomParallelizable.scala" -> Set(
      (473, 476), // Method 'par' overrides nothing
    ),
    "scala/collection/LinearSeqLike.scala" -> Set(
      (2457, 2468), // Recursive call not in tail position (in @tailrec annotated method)
    ),
    "scala/collection/immutable/HashMap.scala" -> Set(
      (5547, 5555), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/collection/immutable/NumericRange.scala" -> Set(
      (5840, 5880), // No implicit arguments of type: Integral[A]
      (5894, 5924), // No implicit arguments of type: Integral[A]
      (9750, 9758), // Overriding type Int does not conform to base type () => Int
      (9750, 9758), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/collection/mutable/ArrayLike.scala" -> Set(
      (1689, 1697), // Cannot resolve symbol getClass
    ),
    "scala/collection/parallel/ParIterableLike.scala" -> Set(
      (36198, 36200), // Type mismatch, expected: T <:< (Nothing, Nothing), actual: T <:< (K, V)
    ),
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2723, 2730), // Cannot resolve symbol subargs
      (2956, 2963), // Cannot resolve symbol subtype
    ),
    "scala/reflect/Manifest.scala" -> Set(
      (3421, 3429), // Overriding type Int does not conform to base type () => Int
      (12572, 12580), // Overriding type String does not conform to base type () => String
      (14226, 14234), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/sys/BooleanProp.scala" -> Set(
      (1744, 1749), // Overriding type Unit does not conform to base type () => Unit
      (1751, 1757), // Overriding type Unit does not conform to base type () => Unit
      (1759, 1766), // Overriding type Unit does not conform to base type () => Unit
      (1768, 1774), // Overriding type Unit does not conform to base type () => Unit
    )
  )
}
