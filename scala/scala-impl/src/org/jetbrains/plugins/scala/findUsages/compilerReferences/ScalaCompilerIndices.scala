package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.{DataInput, DataOutput}
import java.util

import scala.collection.JavaConverters._

import com.intellij.openapi.util.io.{DataInputOutputUtilRt => ioutil}
import com.intellij.util.indexing.{DataIndexer, IndexExtension, IndexId}
import com.intellij.util.io.{DataExternalizer, KeyDescriptor}
import org.jetbrains.jps.backwardRefs.{CompilerRef, CompilerRefDescriptor}

private object ScalaCompilerIndices {
  val backwardUsages: IndexId[CompilerRef, Set[Int]]            = IndexId.create("sc.back.refs")
  val backwardHierarchy: IndexId[CompilerRef, Set[CompilerRef]] = IndexId.create("sc.back.hierarchy")

  val getIndices: util.Collection[_ <: IndexExtension[_, _, _ >: CompiledScalaFile]] = util.Arrays.asList(
    backUsagesExtension,
    backHierarchyExtension
  )

  private[this] val refDescriptor = CompilerRefDescriptor.INSTANCE
  
  private[this] def backUsagesExtension: IndexExtension[CompilerRef, Set[Int], CompiledScalaFile] =
    new IndexExtension[CompilerRef, Set[Int], CompiledScalaFile] {
      override def getVersion: Int                                                   = 0
      override def getName: IndexId[CompilerRef, Set[Int]]                           = backwardUsages
      override def getIndexer: DataIndexer[CompilerRef, Set[Int], CompiledScalaFile] = _.refs
      override def getKeyDescriptor: KeyDescriptor[CompilerRef]                      = refDescriptor
      override def getValueExternalizer: DataExternalizer[Set[Int]]                  = setExternalizer(ioutil.readINT, ioutil.writeINT)
    }

  private[this] def backHierarchyExtension: IndexExtension[CompilerRef, Set[CompilerRef], CompiledScalaFile] =
    new IndexExtension[CompilerRef, Set[CompilerRef], CompiledScalaFile] {
      override def getVersion: Int                                                           = 0
      override def getName: IndexId[CompilerRef, Set[CompilerRef]]                           = backwardHierarchy
      override def getIndexer: DataIndexer[CompilerRef, Set[CompilerRef], CompiledScalaFile] = _.backwardHierarchy
      override def getKeyDescriptor: KeyDescriptor[CompilerRef]                              = refDescriptor
      override def getValueExternalizer: DataExternalizer[Set[CompilerRef]]                  = setExternalizer(refDescriptor.read, refDescriptor.save)
    }

  private[this] def setExternalizer[T](reader: DataInput => T, writer: (DataOutput, T) => Unit): DataExternalizer[Set[T]] =
    new DataExternalizer[Set[T]] {
      override def read(in: DataInput): Set[T] = ioutil.readSeq[T](in, () => reader(in)).asScala.toSet

      override def save(out: DataOutput, xs: Set[T]): Unit =
        ioutil.writeSeq(out, xs.asJava, writer(out, _: T))
    }
}
