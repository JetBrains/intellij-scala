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
      (3533, 3541), // Method 'getClass' needs override modifier
      (3533, 3541), // Method 'getClass' cannot override final member
    ),
    "scala/AnyRef.scala" -> Set(
      (821, 827), // Method 'equals' needs override modifier
      (1013, 1021), // Method 'hashCode' needs override modifier
      (1361, 1369), // Method 'toString' needs override modifier
      (3153, 3154), // Cannot resolve symbol !
      (3160, 3162), // Cannot resolve overloaded method 'eq'
      (3512, 3514), // Cannot resolve overloaded method 'eq'
      (3547, 3549), // Cannot resolve overloaded method 'eq'
      (4420, 4425), // 'final' modifier can't be used with incomplete members
      (4594, 4599), // 'final' modifier can't be used with incomplete members
      (4779, 4784), // 'final' modifier can't be used with incomplete members
      (5094, 5099), // 'final' modifier can't be used with incomplete members
      (5363, 5368), // 'final' modifier can't be used with incomplete members
    ),
    "scala/Predef.scala" -> Set(
      (6386, 6395), // Cannot resolve symbol `package`
    ),
    "scala/Singleton.scala" -> Set(
      (1797, 1802), // 'final' modifier not allowed with trait
    ),
    "scala/collection/immutable/SortedMap.scala" -> Set(
      (7427, 7521), // Expression of type mutable.Builder[(K, Nothing), WithDefault[K, V]] doesn't conform to expected type mutable.Builder[(K, V), WithDefault[K, V]]
    ),
    "scala/collection/mutable/HashMap.scala" -> Set(
      (17777, 17799), // Cannot resolve symbol DeserializationFactory
    ),
    "scala/collection/mutable/SortedMap.scala" -> Set(
      (4055, 4149), // Expression of type mutable.Builder[(K, Nothing), Nothing] doesn't conform to expected type mutable.Builder[(K, V), WithDefault[K, V]]
      (4090, 4105), // Type mismatch, expected: mutable.SortedMap[K, Nothing], actual: SortedMap[K, V]
    ),
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2771, 2778), // Cannot resolve symbol subargs
      (3004, 3011), // Cannot resolve symbol subtype
    ),
    "scala/reflect/Manifest.scala" -> Set(
      (7478, 7486), // Overriding type Int does not conform to base type () => Int
      (17061, 17069), // Overriding type String does not conform to base type () => String
      (18715, 18723), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/runtime/ClassValueCompat.scala" -> Set(
      (512, 531), // Cannot resolve symbol classValueAvailable
    )
  )
}
