package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion

class ScalaLibraryHighlightingTest_2_13 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  //
  // NOTE: we expect errors in Any.scala, AnyRef.scala, Singleton.scala
  // This is a special synthetic scala classes which is not meant to be compiled to .class file
  // See https://github.com/scala/bug/issues/12958
  //

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/Any.scala" -> Set(
      (3620, 3628), // Method 'getClass' needs override modifier
      (3620, 3628), // Method 'getClass' cannot override final member
    ),
    "scala/AnyRef.scala" -> Set(
      (812, 818), // Method 'equals' needs override modifier
      (1004, 1012), // Method 'hashCode' needs override modifier
      (1352, 1360), // Method 'toString' needs override modifier
      (3144, 3145), // Cannot resolve symbol !
      (4411, 4416), // 'final' modifier can't be used with incomplete members
      (4585, 4590), // 'final' modifier can't be used with incomplete members
      (4770, 4775), // 'final' modifier can't be used with incomplete members
      (5085, 5090), // 'final' modifier can't be used with incomplete members
      (5354, 5359), // 'final' modifier can't be used with incomplete members
    ),
    "scala/Predef.scala" -> Set(
      (6377, 6386), // Cannot resolve symbol `package`
    ),
    "scala/Singleton.scala" -> Set(
      (1788, 1793), // 'final' modifier not allowed with trait
    ),
    "scala/StringContext.scala" -> Set(
      (3002, 3024), // Cannot resolve symbol InvalidEscapeException
      (7212, 7234), // Cannot resolve symbol InvalidEscapeException
    ),
    "scala/collection/Map.scala" -> Set(
      (4117, 4121), // Type mismatch, expected: MapView.SomeMapOps[NotInferredK, NotInferredV], actual: MapOps[K, V, CC, C]
      (11341, 11345), // Type mismatch, expected: MapView.SomeMapOps[NotInferredK, NotInferredV], actual: MapOps[K, V, CC, C]
      (11886, 11890), // Type mismatch, expected: MapView.SomeMapOps[NotInferredK, NotInferredV], actual: MapOps[K, V, CC, C]
    ),
    "scala/collection/StrictOptimizedSortedMapOps.scala" -> Set(
      (1785, 1797), // Cannot resolve symbol asInstanceOf
      (1809, 1810), // Cannot resolve symbol +
      (1873, 1879), // Cannot resolve symbol concat
    ),
    "scala/collection/immutable/IntMap.scala" -> Set(
      (10799, 10804), // Method annotated with @tailrec contains no recursive calls
    ),
    "scala/collection/immutable/LongMap.scala" -> Set(
      (10803, 10808), // Method annotated with @tailrec contains no recursive calls
    ),
    "scala/collection/immutable/SortedMap.scala" -> Set(
      (7418, 7512), // Expression of type mutable.Builder[(K, Nothing), WithDefault[K, V]] doesn't conform to expected type mutable.Builder[(K, V), WithDefault[K, V]]
    ),
    "scala/collection/mutable/HashMap.scala" -> Set(
      (17768, 17790), // Cannot resolve symbol DeserializationFactory
    ),
    "scala/collection/mutable/SortedMap.scala" -> Set(
      (2271, 2277), // Cannot resolve symbol addOne
      (4046, 4140), // Expression of type mutable.Builder[(K, Nothing), Nothing] doesn't conform to expected type mutable.Builder[(K, V), WithDefault[K, V]]
      (4081, 4096), // Type mismatch, expected: mutable.SortedMap[K, Nothing], actual: SortedMap[K, V]
    ),
    "scala/concurrent/duration/DurationConversions.scala" -> Set(
      (1748, 1762), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1827, 1841), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1906, 1920), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2075, 2090), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2155, 2170), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2235, 2250), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2405, 2420), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2485, 2500), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2565, 2580), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2730, 2740), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2890, 2900), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (3048, 3056), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (3203, 3210), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
    ),
    "scala/package.scala" -> Set(
      (3804, 3812), // Cannot resolve symbol nonEmpty
      (3822, 3826), // Cannot resolve symbol head
      (3830, 3834), // Cannot resolve symbol tail
      (3979, 3987), // Cannot resolve symbol nonEmpty
      (3997, 4001), // Cannot resolve symbol head
      (4005, 4009), // Cannot resolve symbol tail
    ),
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2762, 2769), // Cannot resolve symbol subargs
      (2995, 3002), // Cannot resolve symbol subtype
    ),
    "scala/reflect/Manifest.scala" -> Set(
      (7469, 7477), // Overriding type Int does not conform to base type () => Int
      (17052, 17060), // Overriding type String does not conform to base type () => String
      (18706, 18714), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/runtime/ClassValueCompat.scala" -> Set(
      (503, 522), // Cannot resolve symbol classValueAvailable
    )
  )
}
