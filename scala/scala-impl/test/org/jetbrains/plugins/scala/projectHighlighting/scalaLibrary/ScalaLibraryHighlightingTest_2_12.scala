package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

class ScalaLibraryHighlightingTest_2_12 extends ScalaLibraryHighlightingTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_12

  override protected val filesWithProblems: Map[String, Set[TextRange]] = Map(
    "scala/Console.scala" -> Set(
      (588, 594), // Cannot resolve symbol StdIn$
    ),
    "scala/Predef.scala" -> Set(
      (6390, 6399), // Cannot resolve symbol `package`
      (20466, 20482), // Scrutinee is incompatible with pattern type, found: Array[AnyRef], required: Array[T]
      (20519, 20536), // Scrutinee is incompatible with pattern type, found: Array[Boolean], required: Array[T]
      (20568, 20582), // Scrutinee is incompatible with pattern type, found: Array[Byte], required: Array[T]
      (20614, 20628), // Scrutinee is incompatible with pattern type, found: Array[Char], required: Array[T]
      (20660, 20676), // Scrutinee is incompatible with pattern type, found: Array[Double], required: Array[T]
      (20708, 20723), // Scrutinee is incompatible with pattern type, found: Array[Float], required: Array[T]
      (20755, 20768), // Scrutinee is incompatible with pattern type, found: Array[Int], required: Array[T]
      (20800, 20814), // Scrutinee is incompatible with pattern type, found: Array[Long], required: Array[T]
      (20846, 20861), // Scrutinee is incompatible with pattern type, found: Array[Short], required: Array[T]
      (20893, 20907), // Scrutinee is incompatible with pattern type, found: Array[Unit], required: Array[T]
      (26358, 26364), // Cannot resolve symbol Array$
      (26529, 26535), // Cannot resolve symbol Array$
    ),
    "scala/StringContext.scala" -> Set(
      (3206, 3228), // Cannot resolve symbol InvalidEscapeException
      (5955, 5977), // Cannot resolve symbol InvalidEscapeException
    ),
    "scala/collection/CustomParallelizable.scala" -> Set(
      (464, 467), // Method 'par' overrides nothing
    ),
    "scala/collection/LinearSeqLike.scala" -> Set(
      (2448, 2459), // Recursive call not in tail position (in @tailrec annotated method)
    ),
    "scala/collection/LinearSeqOptimized.scala" -> Set(
      (6627, 6637), // Type mismatch, expected: (GenIterable[B] with LinearSeq[_$1]) forSome {type _$1}, actual: LinearSeq[_]
      (6627, 6637), // Expression of type LinearSeq[_] doesn't conform to expected type (GenIterable[B] with LinearSeq[_$1]) forSome {type _$1}
    ),
    "scala/collection/SetLike.scala" -> Set(
      (5992, 6007), // Pattern type is incompatible with expected type, found: TreeSet[A], required: SetLike[A, This]
    ),
    "scala/collection/SortedSetLike.scala" -> Set(
      (1333, 1346), // Type mismatch, expected: Iterator[_$1], actual: Iterator[A]
    ),
    "scala/collection/concurrent/TrieMap.scala" -> Set(
      (23034, 23049), // Type mismatch, expected: Hashing[K], actual: Hashing.Default[Nothing]
      (23065, 23067), // Unspecified value parameters: hashf: Hashing[K], ef: Equiv[K]
    ),
    "scala/collection/convert/AsScalaConverters.scala" -> Set(
      (7279, 7293), // Expression of type Map[_, _] doesn't conform to expected type Map[A, B]
    ),
    "scala/collection/convert/WrapAsScala.scala" -> Set(
      (8759, 8773), // Expression of type Map[_, _] doesn't conform to expected type Map[A, B]
    ),
    "scala/collection/generic/ClassTagTraversableFactory.scala" -> Set(
      (1010, 1032), // Cannot resolve symbol genericClassTagBuilder
    ),
    "scala/collection/generic/GenTraversableFactory.scala" -> Set(
      (2221, 2235), // Cannot resolve symbol genericBuilder
    ),
    "scala/collection/generic/Growable.scala" -> Set(
      (1658, 1660), // Type mismatch, expected: LinearSeq[A], actual: (TraversableOnce[A] with LinearSeq[_$1]) forSome {type _$1}
    ),
    "scala/collection/generic/ImmutableSetFactory.scala" -> Set(
      (615, 627), // Cannot resolve symbol asInstanceOf
      (701, 709), // Type mismatch, expected: CC[A], actual: Any
    ),
    "scala/collection/generic/OrderedTraversableFactory.scala" -> Set(
      (658, 679), // Cannot resolve symbol genericOrderedBuilder
    ),
    "scala/collection/generic/ParFactory.scala" -> Set(
      (1295, 1310), // Cannot resolve symbol genericCombiner
    ),
    "scala/collection/generic/ParMapFactory.scala" -> Set(
      (1593, 1605), // Cannot resolve symbol asInstanceOf
      (1568, 1586), // Cannot resolve symbol genericMapCombiner
    ),
    "scala/collection/generic/ParSetFactory.scala" -> Set(
      (946, 961), // Cannot resolve symbol genericCombiner
    ),
    "scala/collection/generic/Subtractable.scala" -> Set(
      (2466, 2480), // Pattern type is incompatible with expected type, found: TreeSet[A], required: Subtractable[A, Repr]
    ),
    "scala/collection/immutable/HashMap.scala" -> Set(
      (5538, 5546), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/collection/immutable/Map.scala" -> Set(
      (2497, 2504), // Method 'updated' overrides nothing
    ),
    "scala/collection/immutable/NumericRange.scala" -> Set(
      (5831, 5871), // No implicit arguments of type: Integral[A]
      (5885, 5915), // No implicit arguments of type: Integral[A]
      (9741, 9749), // Overriding type Int does not conform to base type () => Int
      (9741, 9749), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/collection/mutable/ArrayLike.scala" -> Set(
      (1689, 1696), // Cannot resolve symbol isArray
      (1680, 1688), // Cannot resolve symbol getClass
    ),
    "scala/collection/mutable/TreeMap.scala" -> Set(
      (1746, 1759), // Type mismatch, expected: mutable.RedBlackTree.Tree[A, B], actual: mutable.RedBlackTree.Tree[Nothing, Nothing]
    ),
    "scala/collection/mutable/TreeSet.scala" -> Set(
      (1914, 1927), // Type mismatch, expected: mutable.RedBlackTree.Tree[A, Null], actual: mutable.RedBlackTree.Tree[Nothing, Null]
    ),
    "scala/collection/mutable/WrappedArray.scala" -> Set(
      (2182, 2213), // Expression of type mutable.WrappedArray[Nothing] doesn't conform to expected type mutable.WrappedArray[T]
    ),
    "scala/collection/parallel/ParIterableLike.scala" -> Set(
      (36189, 36191), // Type mismatch, expected: T <:< (Nothing, Nothing), actual: T <:< (K, V)
    ),
    "scala/collection/parallel/RemainsIterator.scala" -> Set(
      (20818, 20819), // Expression of type Seq[Zipped[S]] doesn't conform to expected type Seq[SeqSplitter[(U, S)]]
    ),
    "scala/collection/parallel/immutable/ParHashMap.scala" -> Set(
      (2688, 2701), // Type mismatch, expected: Iterator[(K, V)], actual: TrieIterator[_]
      (3306, 3309), // Type mismatch, expected: Iterator[(K, V)], actual: Iterator[Any]
      (3356, 3359), // Type mismatch, expected: Iterator[(K, V)], actual: Iterator[Any]
      (4247, 4256), // Type mismatch, expected: k, actual: Any
      (4302, 4311), // Type mismatch, expected: K, actual: Any
      (9851, 9857), // Cannot resolve symbol result
      (9896, 9903), // Type mismatch, expected: K, actual: Any
      (10040, 10046), // Cannot resolve symbol result
      (10107, 10116), // Type mismatch, expected: ListMap[K, Repr], actual: ListMap[Any, Any]
      (10245, 10304), // Type mismatch, expected: HashMap[k, v], actual: HashMap[Any, Any]
      (10245, 10304), // Expression of type HashMap[Any, Any] doesn't conform to expected type HashMap[k, v]
      (10263, 10275), // Type mismatch, expected: HashMap[K, Combiner[V, Repr]], actual: HashMap[_, _]
    ),
    "scala/collection/parallel/immutable/ParHashSet.scala" -> Set(
      (2539, 2552), // Type mismatch, expected: Iterator[T], actual: TrieIterator[_]
      (3128, 3131), // Type mismatch, expected: Iterator[T], actual: Iterator[Any]
      (3178, 3181), // Type mismatch, expected: Iterator[T], actual: Iterator[Any]
    ),
    "scala/collection/parallel/mutable/ParTrieMap.scala" -> Set(
      (1658, 1669), // Type mismatch, expected: TrieMap[K, V], actual: TrieMap[Nothing, Nothing]
    ),
    "scala/concurrent/ExecutionContext.scala" -> Set(
      (7022, 7028), // Cannot resolve symbol global
      (7004, 7021), // Cannot resolve symbol ExecutionContext$
      (7483, 7498), // Cannot resolve symbol defaultReporter
      (7465, 7482), // Cannot resolve symbol ExecutionContext$
      (7990, 7996), // Cannot resolve symbol global
      (7972, 7989), // Cannot resolve symbol ExecutionContext$
      (8417, 8423), // Cannot resolve symbol global
      (8399, 8416), // Cannot resolve symbol ExecutionContext$
      (8836, 8851), // Cannot resolve symbol defaultReporter
      (8818, 8835), // Cannot resolve symbol ExecutionContext$
      (8996, 9002), // Cannot resolve symbol global
      (8978, 8995), // Cannot resolve symbol ExecutionContext$
    ),
    "scala/concurrent/duration/Duration.scala" -> Set(
      (4096, 4101), // Cannot resolve symbol apply
      (15098, 15104), // Cannot resolve symbol toUnit
      (15150, 15159), // Cannot resolve symbol fromNanos
      (15405, 15408), // Cannot resolve symbol Inf
    ),
    "scala/concurrent/duration/DurationConversions.scala" -> Set(
      (1420, 1434), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1497, 1511), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1568, 1582), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1731, 1746), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1810, 1825), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (1883, 1898), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2047, 2062), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2126, 2141), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2199, 2214), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2353, 2363), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2502, 2512), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2645, 2653), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
      (2783, 2790), // Expression of type Classifier[C]#R doesn't conform to expected type ev.R
    ),
    "scala/reflect/ClassManifestDeprecatedApis.scala" -> Set(
      (2714, 2721), // Cannot resolve symbol subargs
      (2947, 2954), // Cannot resolve symbol subtype
      (7575, 7594), // Pattern type is incompatible with expected type, found: Class[Byte], required: Class[T]
      (7648, 7668), // Pattern type is incompatible with expected type, found: Class[Short], required: Class[T]
      (7722, 7746), // Pattern type is incompatible with expected type, found: Class[Character], required: Class[T]
      (7795, 7817), // Pattern type is incompatible with expected type, found: Class[Integer], required: Class[T]
      (7867, 7886), // Pattern type is incompatible with expected type, found: Class[Long], required: Class[T]
      (7940, 7960), // Pattern type is incompatible with expected type, found: Class[Float], required: Class[T]
      (8014, 8035), // Pattern type is incompatible with expected type, found: Class[Double], required: Class[T]
      (8089, 8111), // Pattern type is incompatible with expected type, found: Class[Boolean], required: Class[T]
      (8165, 8184), // Pattern type is incompatible with expected type, found: Class[Void], required: Class[T]
    ),
    "scala/reflect/ClassTag.scala" -> Set(
      (767, 775), // Cannot resolve symbol TypeTags
      (763, 766), // Cannot resolve symbol api
      (1519, 1527), // Cannot resolve symbol TypeTags
      (1515, 1518), // Cannot resolve symbol api
    ),
    "scala/reflect/Manifest.scala" -> Set(
      (3412, 3420), // Overriding type Int does not conform to base type () => Int
      (12563, 12571), // Overriding type String does not conform to base type () => String
      (14217, 14225), // Overriding type Int does not conform to base type () => Int
    ),
    "scala/sys/BooleanProp.scala" -> Set(
      (1735, 1740), // Overriding type Unit does not conform to base type () => Unit
      (1742, 1748), // Overriding type Unit does not conform to base type () => Unit
      (1750, 1757), // Overriding type Unit does not conform to base type () => Unit
      (1759, 1765), // Overriding type Unit does not conform to base type () => Unit
    ),
    "scala/util/Sorting.scala" -> Set(
      (8542, 8558), // Scrutinee is incompatible with pattern type, found: Array[AnyRef], required: Array[T]
      (8827, 8840), // Scrutinee is incompatible with pattern type, found: Array[Int], required: Array[T]
      (8934, 8937), // Type mismatch, expected: Ordering[Int], actual: Ordering[T]
      (8948, 8964), // Scrutinee is incompatible with pattern type, found: Array[Double], required: Array[T]
      (9003, 9006), // Type mismatch, expected: Ordering[Double], actual: Ordering[T]
      (9082, 9096), // Scrutinee is incompatible with pattern type, found: Array[Long], required: Array[T]
      (9191, 9194), // Type mismatch, expected: Ordering[Long], actual: Ordering[T]
      (9205, 9220), // Scrutinee is incompatible with pattern type, found: Array[Float], required: Array[T]
      (9259, 9262), // Type mismatch, expected: Ordering[Float], actual: Ordering[T]
      (9339, 9353), // Scrutinee is incompatible with pattern type, found: Array[Char], required: Array[T]
      (9448, 9451), // Type mismatch, expected: Ordering[Char], actual: Ordering[T]
      (9462, 9476), // Scrutinee is incompatible with pattern type, found: Array[Byte], required: Array[T]
      (9571, 9574), // Type mismatch, expected: Ordering[Byte], actual: Ordering[T]
      (9585, 9600), // Scrutinee is incompatible with pattern type, found: Array[Short], required: Array[T]
      (9696, 9699), // Type mismatch, expected: Ordering[Short], actual: Ordering[T]
      (9710, 9727), // Scrutinee is incompatible with pattern type, found: Array[Boolean], required: Array[T]
      (9815, 9818), // Type mismatch, expected: Ordering[Boolean], actual: Ordering[T]
    ),
    "scala/util/control/TailCalls.scala" -> Set(
      (2034, 2035), // Type mismatch, expected: b1 => TailRec[NotInferredB], actual: A => TailRec[B]
      (2023, 2024), // Type mismatch, expected: a1, actual: Any
      (2375, 2376), // Type mismatch, expected: Nothing, actual: Any
      (2432, 2433), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
      (2491, 2492), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
      (2480, 2481), // Type mismatch, expected: Nothing, actual: Any
      (2760, 2761), // Type mismatch, expected: Nothing, actual: Any
      (2806, 2807), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
      (2871, 2872), // Type mismatch, expected: Any => TailRec[NotInferredB], actual: Nothing => TailRec[A]
      (2860, 2861), // Type mismatch, expected: Nothing, actual: Any
    ),
    "scala/util/matching/Regex.scala" -> Set(
      (23224, 23229), // Cannot resolve overloaded constructor `Regex`
    )
  )
}




