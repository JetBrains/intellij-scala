package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion

class ScalaLibraryHighlightingTest_2_13 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/Array.scala" -> Set(
      (4316, 4335), // Pattern type is incompatible with expected type, found: Array[BoxedUnit], required: Array[A]
      (4396, 4412), // Pattern type is incompatible with expected type, found: Array[AnyRef], required: Array[A]
      (4467, 4480), // Pattern type is incompatible with expected type, found: Array[Int], required: Array[A]
      (4538, 4554), // Pattern type is incompatible with expected type, found: Array[Double], required: Array[A]
      (4609, 4623), // Pattern type is incompatible with expected type, found: Array[Long], required: Array[A]
      (4680, 4695), // Pattern type is incompatible with expected type, found: Array[Float], required: Array[A]
      (4751, 4765), // Pattern type is incompatible with expected type, found: Array[Char], required: Array[A]
      (4822, 4836), // Pattern type is incompatible with expected type, found: Array[Byte], required: Array[A]
      (4893, 4908), // Pattern type is incompatible with expected type, found: Array[Short], required: Array[A]
      (4964, 4981), // Pattern type is incompatible with expected type, found: Array[Boolean], required: Array[A]
    ),
    "scala/Predef.scala" -> Set(
      (6377, 6386), // Cannot resolve symbol `package`
    ),
    "scala/StringContext.scala" -> Set(
      (2986, 3008), // Cannot resolve symbol InvalidEscapeException
      (7176, 7198), // Cannot resolve symbol InvalidEscapeException
    ),
    "scala/collection/Map.scala" -> Set(
      (4117, 4121), // Type mismatch, expected: MapView.SomeMapOps[NotInferredK, NotInferredV], actual: MapOps[K, V, CC, C]
      (10363, 10367), // Type mismatch, expected: MapView.SomeMapOps[NotInferredK, NotInferredV], actual: MapOps[K, V, CC, C]
      (10908, 10912), // Type mismatch, expected: MapView.SomeMapOps[NotInferredK, NotInferredV], actual: MapOps[K, V, CC, C]
    ),
    "scala/collection/Set.scala" -> Set(
      (7612, 7654), // Pattern type is incompatible with expected type, found: Set.Set1[A], required: SetOps[A, CC, C]
      (7657, 7698), // Pattern type is incompatible with expected type, found: Set.Set2[A], required: SetOps[A, CC, C]
      (7701, 7742), // Pattern type is incompatible with expected type, found: Set.Set3[A], required: SetOps[A, CC, C]
      (7745, 7786), // Pattern type is incompatible with expected type, found: Set.Set4[A], required: SetOps[A, CC, C]
    ),
    "scala/collection/StrictOptimizedSortedMapOps.scala" -> Set(
      (1785, 1797), // Cannot resolve symbol asInstanceOf
      (1809, 1810), // Cannot resolve symbol +
      (1818, 1830), // Cannot resolve symbol asInstanceOf
      (1873, 1879), // Cannot resolve symbol concat
      (1887, 1899), // Cannot resolve symbol asInstanceOf
    ),
    "scala/collection/concurrent/TrieMap.scala" -> Set(
      (11352, 11354), // Type mismatch, expected: TNode[K, V], actual: (MainNode[K, V] with TNode[_$1, _$2]) forSome {type _$1; type _$2}
      (26365, 26380), // Type mismatch, expected: Hashing[K], actual: Hashing.Default[Nothing]
      (26396, 26398), // Unspecified value parameters: hashf: Hashing[K], ef: Equiv[K]
    ),
    "scala/collection/immutable/ArraySeq.scala" -> Set(
      (10990, 11006), // Pattern type is incompatible with expected type, found: Array[AnyRef], required: Array[T]
      (11041, 11054), // Pattern type is incompatible with expected type, found: Array[Int], required: Array[T]
      (11084, 11100), // Pattern type is incompatible with expected type, found: Array[Double], required: Array[T]
      (11130, 11144), // Pattern type is incompatible with expected type, found: Array[Long], required: Array[T]
      (11174, 11189), // Pattern type is incompatible with expected type, found: Array[Float], required: Array[T]
      (11219, 11233), // Pattern type is incompatible with expected type, found: Array[Char], required: Array[T]
      (11263, 11277), // Pattern type is incompatible with expected type, found: Array[Byte], required: Array[T]
      (11307, 11322), // Pattern type is incompatible with expected type, found: Array[Short], required: Array[T]
      (11352, 11369), // Pattern type is incompatible with expected type, found: Array[Boolean], required: Array[T]
      (11399, 11413), // Pattern type is incompatible with expected type, found: Array[Unit], required: Array[T]
    ),
    "scala/collection/immutable/HashMap.scala" -> Set(
      (1612, 1625), // Type mismatch, expected: BitmapIndexedMapNode[K, V], actual: BitmapIndexedMapNode[Nothing, Nothing]
    ),
    "scala/collection/immutable/HashSet.scala" -> Set(
      (1326, 1339), // Type mismatch, expected: BitmapIndexedSetNode[A], actual: BitmapIndexedSetNode[Nothing]
    ),
    "scala/collection/immutable/IntMap.scala" -> Set(
      (10799, 10804), // Method annotated with @tailrec contains no recursive calls
    ),
    "scala/collection/immutable/LongMap.scala" -> Set(
      (10790, 10795), // Method annotated with @tailrec contains no recursive calls
    ),
    "scala/collection/immutable/SortedMap.scala" -> Set(
      (7418, 7512), // Expression of type mutable.Builder[(K, Nothing), WithDefault[K, V]] doesn't conform to expected type mutable.Builder[(K, V), WithDefault[K, V]]
    ),
    "scala/collection/mutable/HashMap.scala" -> Set(
      (17245, 17267), // Cannot resolve symbol DeserializationFactory
      (17273, 17299), // No constructor accessible from here
    ),
    "scala/collection/mutable/SortedMap.scala" -> Set(
      (2271, 2277), // Cannot resolve symbol addOne
      (4046, 4140), // Expression of type mutable.Builder[(K, Nothing), Nothing] doesn't conform to expected type mutable.Builder[(K, V), WithDefault[K, V]]
      (4081, 4096), // Type mismatch, expected: mutable.SortedMap[K, Nothing], actual: SortedMap[K, V]
    ),
    "scala/collection/mutable/TreeMap.scala" -> Set(
      (1614, 1627), // Type mismatch, expected: mutable.RedBlackTree.Tree[K, V], actual: mutable.RedBlackTree.Tree[Nothing, Nothing]
    ),
    "scala/collection/mutable/TreeSet.scala" -> Set(
      (1714, 1727), // Type mismatch, expected: mutable.RedBlackTree.Tree[A, Null], actual: mutable.RedBlackTree.Tree[Nothing, Null]
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
    "scala/concurrent/impl/Promise.scala" -> Set(
      (6498, 6506), // Type mismatch, expected: T, actual: Any
      (6913, 6922), // Type mismatch, expected: U, actual: Any
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
      (7552, 7571), // Pattern type is incompatible with expected type, found: Class[Byte], required: Class[T]
      (7625, 7645), // Pattern type is incompatible with expected type, found: Class[Short], required: Class[T]
      (7699, 7723), // Pattern type is incompatible with expected type, found: Class[Character], required: Class[T]
      (7772, 7794), // Pattern type is incompatible with expected type, found: Class[Integer], required: Class[T]
      (7844, 7863), // Pattern type is incompatible with expected type, found: Class[Long], required: Class[T]
      (7917, 7937), // Pattern type is incompatible with expected type, found: Class[Float], required: Class[T]
      (7991, 8012), // Pattern type is incompatible with expected type, found: Class[Double], required: Class[T]
      (8066, 8088), // Pattern type is incompatible with expected type, found: Class[Boolean], required: Class[T]
      (8142, 8161), // Pattern type is incompatible with expected type, found: Class[Void], required: Class[T]
    ),
    "scala/reflect/Manifest.scala" -> Set(
      (7469, 7477), // Overriding type Int does not conform to base type () => Int
      (16810, 16818), // Overriding type String does not conform to base type () => String
      (18464, 18472), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/runtime/ClassValueCompat.scala" -> Set(
      (503, 522), // Cannot resolve symbol classValueAvailable
    ),
    "scala/util/Sorting.scala" -> Set(
      (8542, 8558), // Pattern type is incompatible with expected type, found: Array[AnyRef], required: Array[T]
      (8840, 8853), // Pattern type is incompatible with expected type, found: Array[Int], required: Array[T]
      (8960, 8963), // Type mismatch, expected: Ordering[Int], actual: Ordering[T]
      (8974, 8990), // Pattern type is incompatible with expected type, found: Array[Double], required: Array[T]
      (9029, 9032), // Type mismatch, expected: Ordering[Double], actual: Ordering[T]
      (9108, 9122), // Pattern type is incompatible with expected type, found: Array[Long], required: Array[T]
      (9230, 9233), // Type mismatch, expected: Ordering[Long], actual: Ordering[T]
      (9244, 9259), // Pattern type is incompatible with expected type, found: Array[Float], required: Array[T]
      (9298, 9301), // Type mismatch, expected: Ordering[Float], actual: Ordering[T]
      (9378, 9392), // Pattern type is incompatible with expected type, found: Array[Char], required: Array[T]
      (9500, 9503), // Type mismatch, expected: Ordering[Char], actual: Ordering[T]
      (9514, 9528), // Pattern type is incompatible with expected type, found: Array[Byte], required: Array[T]
      (9636, 9639), // Type mismatch, expected: Ordering[Byte], actual: Ordering[T]
      (9650, 9665), // Pattern type is incompatible with expected type, found: Array[Short], required: Array[T]
      (9774, 9777), // Type mismatch, expected: Ordering[Short], actual: Ordering[T]
      (9788, 9805), // Pattern type is incompatible with expected type, found: Array[Boolean], required: Array[T]
      (9906, 9909), // Type mismatch, expected: Ordering[Boolean], actual: Ordering[T]
    ),
    "scala/util/control/TailCalls.scala" -> Set(
      (2031, 2032), // Type mismatch, expected: a1, actual: Any
      (2042, 2043), // Type mismatch, expected: b1 => TailRec[NotInferredB], actual: A => TailRec[B]
      (2383, 2384), // Type mismatch, expected: Nothing, actual: Any
      (2440, 2441), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
      (2488, 2489), // Type mismatch, expected: Nothing, actual: Any
      (2499, 2500), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
      (2768, 2769), // Type mismatch, expected: Nothing, actual: Any
      (2814, 2815), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
      (2868, 2869), // Type mismatch, expected: Nothing, actual: Any
      (2879, 2880), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
    )
  )
}
