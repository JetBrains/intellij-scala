package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.text.TextToTextTestBase
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Content
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaLibrary_3_8_Test extends TextToTextTestBase(
  dependencies = Seq.empty,
  packages = Seq("scala"),
  minClassCount = 910,
  classExceptions = Set(
    "scala.EmptyTuple", // duplicate JARs in RC3
    "scala.NamedTuple", // def map[F[_]](f: [t] => t => F[t])
    "scala.NamedTupleDecomposition", // non-absolute paths in match types
    "scala.Tuple", // non-absolute paths in match types
    "scala.caps.Contains", // with {} | with
    "scala.collection.IndexedSeqView", // extends Id | Id[_root_.scala.Nothing]
    "scala.collection.IterableOnceOps", // "\"
    "scala.collection.SeqView", // extends Appended | Appended[_root_.scala.Nothing]
    "scala.collection.generic.IsIterableLowPriority", // Cannot resolve _root_.scala.collection.generic.IsMap
    "scala.collection.immutable.NumericRange", // extends NumericRange | NumericRange[T]
    "scala.collection.mutable.CheckedIndexedSeqView", // Id | Id[Nothing]
    "scala.collection.mutable.CollisionProofHashMap", // Cannot resolve CollisionProofHashMap.LLNode[K, V]
    "scala.concurrent.impl.Promise", // ? => ? | Function1[?, ?]
    "scala.deriving.Mirror", // duplicate JARs in RC3
    "scala.quoted.ToExpr", // duplicate JARs in RC3
    "scala.runtime.FunctionXXL", // duplicate JARs in RC3
    "scala.runtime.TupleXXL", // duplicate JARs in RC3
    "scala.runtime.Tuples", // duplicate JARs in RC3
    "scala.sys.process.ProcessImpl", // Unknown type
  ),
  withSources = true,
  sourceExceptions = Set(
    "scala.Array", // from: Array[A] | Unit
    "scala.AnyVal", // getClass()
    "scala.Boolean", // private (), override def getClass()
    "scala.Byte", // private (), override def getClass(), MinValue/MaxValue
    "scala.Char", // private (), override def getClass(), MinValue/MaxValue
    "scala.Double", // private (), override def getClass(), MinValue/MaxValue
    "scala.Enumeration", // SerialVersionUID(0 - 3501153230598116017L), private[ValueSet] () | private (), Enumeration.this.ValueSet | Enumeration.this.Value, implicitNotFound | implicitNotFound(ValueSet.ordMsg)
    "scala.Float", // private (), override def getClass(), MinValue/MaxValue
    "scala.Int", // private (), override def getClass(), MinValue/MaxValue
    "scala.Long", // private (), override def getClass(), MinValue/MaxValue
    "scala.Predef", // elidable | elidable(ASSERTION), @deprecated | @deprecated @deprecated
    "scala.Short", // private (), override def getClass(), MinValue/MaxValue
    "scala.StringContext", //  = ??? | N/A (macro ???)
    "scala.Unit", // private (), override def getClass()
    "scala.annotation.MacroAnnotation", // $1 | quotes
    "scala.annotation.elidable", // MinValue/MaxValue
    "scala.caps.Pure", // N/A | { this: _root_.scala.caps.Pure => }
    "scala.collection.IndexedSeqSlidingIterator", // { def hasNext: _root_.scala.Boolean = ???; def next(): C = ??? } | N/A
    "scala.collection.concurrent.INode", // Object | AnyRef
    "scala.collection.convert.StreamExtensions", // self type, private type
    "scala.collection.convert.impl.IteratorStepperBase", // final var Int | 16
    "scala.collection.generic.DefaultSerializationProxy", // { ... } | N/A
    "scala.collection.immutable.::", // private | private[::]
    "scala.collection.immutable.Node", // 31 | (1 << BitPartitionSize) - 1
    "scala.collection.immutable.Stream", // \" | "
    "scala.concurrent.ExecutionContext", // \n in annotation
    "scala.concurrent.Future", // scala.concurrent.Future.never.type | never.this.type
    "scala.concurrent.SyncChannel", // private type | N/A
    "scala.io.AnsiColor", // Escape \u001b
    "scala.io.Position", // 31 - LINE_BITS
    "scala.math.Numeric", // private type | N/A
    "scala.quoted.FromExpr", // scala.collection.immutable.Seq | scala.Seq
    "scala.quoted.runtime.QuoteMatching", // ? <: _root_.scala.AnyKind | ?
    "scala.quoted.runtime.QuoteUnpickler", // ? <: _root_.scala.AnyKind | ?
    "scala.sys.process.ProcessBuilderImpl", // ProcessBuilderImpl.this.IStreamBuilder | _root_.scala.sys.process.ProcessBuilder.IStreamBuilder
    "scala.sys.process.processInternal", // : Boolean | = props contains "scala.process.debug"
  ),
  transformed = {
    case (Content.DecompiledVsSourceOutline, s) => s
      .replaceAll(raw"@_root_\.scala\.transient\s+", "")
      .replaceAll(raw"(?<=extends )_root_\.scala\.\*:\[.+], (?=_root_\.scala\.Product)", "") // class TupleN extends _root_.scala.*:[T1, _root_.scala.EmptyTuple.type]
    case (Content.SourceOutline, s) => s
      .replaceAll(raw"@_root_\.scala\.(?:transient|annotation\.internal\.preview|annotation\.internal\.sharable)\s+", "")
      .replaceAll(raw"(?<=@_root_\.scala\.specialized)\(.+?\)", "") // specialized | specialized(Specializable.Primitives)
      .replaceAll(raw"(?<=@_root_\.scala\.throws)\[_root_\.scala\.(\w+)]\(classOf\[\1]\)", "[_root_.java.lang.$1]") // java.lang.IndexOutOfBoundsException | scala.IndexOutOfBoundsException
      .replaceAll(raw"(?<=@_root_\.scala\.throws)\[_root_\.scala\.(\w+\.)([\w.]+)]\(classOf\[\2]\)", "[_root_.java.util.$1$2]") // java.util.concurrent.TimeoutException | scala.concurrent.TimeoutException
    case (_, s) => s
  },
  aliasScala = false) {

  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_8

  override protected val includeScalaLibrarySources: Boolean = true
}
