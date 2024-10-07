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
      (6390,6399), // Cannot resolve symbol `package`
    ),
    "scala/StringContext.scala" -> Set(
      (3206,3228), // Cannot resolve symbol InvalidEscapeException
      (5955,5977), // Cannot resolve symbol InvalidEscapeException
    ),
    "scala/collection/CustomParallelizable.scala" -> Set(
      (464,467), // Method 'par' overrides nothing
    ),
    "scala/collection/LinearSeqLike.scala" -> Set(
      (2448,2459), // Recursive call not in tail position (in @tailrec annotated method)
    ),
    "scala/collection/immutable/HashMap.scala" -> Set(
      (5538,5546), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/collection/immutable/NumericRange.scala" -> Set(
      (5831,5871), // No implicit arguments of type: Integral[A]
      (5885,5915), // No implicit arguments of type: Integral[A]
      (9741,9749), // Overriding type Int does not conform to base type () => Int
      (9741,9749), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/collection/mutable/ArrayLike.scala" -> Set(
      (1680,1688), // Cannot resolve symbol getClass
    ),
    "scala/collection/parallel/ParIterableLike.scala" -> Set(
      (36189,36191), // Type mismatch, expected: T <:< (Nothing, Nothing), actual: T <:< (K, V)
    ),
    "scala/concurrent/duration/DurationConversions.scala" -> Set(
      (1420,1434), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1497,1511), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1568,1582), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1731,1746), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1810,1825), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1883,1898), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2047,2062), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2126,2141), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2199,2214), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2353,2363), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2502,2512), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2645,2653), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2783,2790), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
    ),
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2714,2721), // Cannot resolve symbol subargs
      (2947,2954), // Cannot resolve symbol subtype
    ),
    "scala/reflect/Manifest.scala" -> Set(
      (3412,3420), // Overriding type Int does not conform to base type () => Int
      (12563,12571), // Overriding type String does not conform to base type () => String
      (14217,14225), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/sys/BooleanProp.scala" -> Set(
      (1735,1740), // Overriding type Unit does not conform to base type () => Unit
      (1742,1748), // Overriding type Unit does not conform to base type () => Unit
      (1750,1757), // Overriding type Unit does not conform to base type () => Unit
      (1759,1765), // Overriding type Unit does not conform to base type () => Unit
    )
  )
}




