package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.{DataInput, DataOutput}
import java.util

import scala.collection.JavaConverters._

import com.intellij.openapi.util.io.{DataInputOutputUtilRt => ioutil}
import com.intellij.util.indexing.{DataIndexer, IndexExtension, IndexId}
import com.intellij.util.io.{DataExternalizer, KeyDescriptor}
import org.jetbrains.jps.backwardRefs.{CompilerRef, CompilerRefDescriptor}

private object ScalaCompilerIndices {
  val backwardUsages: IndexId[CompilerRef, Seq[Int]]       = IndexId.create("sc.back.refs")
  val backwardHierarchy: IndexId[CompilerRef, CompilerRef] = IndexId.create("sc.back.hierarchy")

  val getIndices: util.Collection[_ <: IndexExtension[_, _, _ >: CompiledScalaFile]] = util.Arrays.asList(
    backUsagesExtension,
    backHierarchyExtension
  )

  private[this] val refDescriptor = CompilerRefDescriptor.INSTANCE
  
  private[this] def backUsagesExtension: IndexExtension[CompilerRef, Seq[Int], CompiledScalaFile] =
    new IndexExtension[CompilerRef, Seq[Int], CompiledScalaFile] {
      override def getVersion: Int                                                   = 0
      override def getName: IndexId[CompilerRef, Seq[Int]]                           = backwardUsages
      override def getIndexer: DataIndexer[CompilerRef, Seq[Int], CompiledScalaFile] = _.refs
      override def getKeyDescriptor: KeyDescriptor[CompilerRef]                      = refDescriptor
      override def getValueExternalizer: DataExternalizer[Seq[Int]]                  = seqExternalizer(ioutil.readINT, ioutil.writeINT)
    }

  private[this] def backHierarchyExtension: IndexExtension[CompilerRef, CompilerRef, CompiledScalaFile] =
    new IndexExtension[CompilerRef, CompilerRef, CompiledScalaFile] {
      override def getVersion: Int                                                      = 0
      override def getName: IndexId[CompilerRef, CompilerRef]                           = backwardHierarchy
      override def getIndexer: DataIndexer[CompilerRef, CompilerRef, CompiledScalaFile] = _.backwardHierarchy
      override def getKeyDescriptor: KeyDescriptor[CompilerRef]                         = refDescriptor
      override def getValueExternalizer: DataExternalizer[CompilerRef]                  = refDescriptor
    }

  private[this] def seqExternalizer[T](reader: DataInput => T, writer: (DataOutput, T) => Unit): DataExternalizer[Seq[T]] =
    new DataExternalizer[Seq[T]] {
      override def read(in: DataInput): Seq[T] = ioutil.readSeq[T](in, () => reader(in)).asScala

      override def save(out: DataOutput, xs: Seq[T]): Unit =
        ioutil.writeSeq(out, xs.asJava, writer(out, _: T))
    }
}
