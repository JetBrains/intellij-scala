package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.text.TextToTextTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaLibrary_3_8_Test extends TextToTextTestBase(
  Seq.empty,
  Seq("scala"), Set.empty, 909,
  Set(
    "scala.NamedTuple", // def map[F[_]](f: [t] => t => F[t])
    "scala.NamedTupleDecomposition", // non-absolute paths in match types
    "scala.Tuple", // non-absolute paths in match types
    "scala.caps.Contains", // with {} | with
    "scala.collection.IndexedSeqView", // extends Id | Id[_root_.scala.Nothing]
    "scala.collection.IterableOnceOps", // "\"
    "scala.collection.SeqView", // extends Appended | Appended[_root_.scala.Nothing]
    "scala.collection.concurrent.TrieMap", // TrieMap.this | TrieMap.this.type, SCL-24660
    "scala.collection.convert.JavaCollectionWrappers", // JListWrapper.this | JListWrapper.this.type, SCL-24660
    "scala.collection.generic.IsIterableLowPriority", // Cannot resolve _root_.scala.collection.generic.IsMap
    "scala.collection.immutable.HashSetBuilder", // HashSetBuilder.this | HashSetBuilder.this.type, SCL-24660
    "scala.collection.immutable.MapBuilderImpl", // MapBuilderImpl.this | MapBuilderImpl.this.type, SCL-24660
    "scala.collection.immutable.NumericRange", // extends NumericRange | NumericRange[T]
    "scala.collection.immutable.SetBuilderImpl", // SetBuilderImpl.this | SetBuilderImpl.this.type, SCL-24660
    "scala.collection.mutable.CheckedIndexedSeqView", // Id | Id[Nothing]
    "scala.collection.mutable.CollisionProofHashMap", // Cannot resolve CollisionProofHashMap.LLNode[K, V]
    "scala.collection.mutable.ListMap", // ListMap.this | ListMap.this.type, SCL-24660
    "scala.collection.mutable.UnrolledBuffer", // UnrolledBuffer.this | UnrolledBuffer.this.type, SCL-24660
    "scala.concurrent.impl.Promise", // ? => ? | Function1[?, ?]
    "scala.sys.BooleanProp", // extends PropImpl | _root_.scala.sys.PropImpl
    "scala.sys.process.ProcessImpl", // Unknown type
  )) {

  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_8
}
