package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.{DataInput, DataOutput}
import java.util

import scala.collection.JavaConverters._

import com.intellij.openapi.util.io.{DataInputOutputUtilRt => ioutil}
import com.intellij.util.indexing.{DataIndexer, IndexExtension, IndexId}
import com.intellij.util.io.{DataExternalizer, KeyDescriptor}
import org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices
import org.jetbrains.jps.backwardRefs.{CompilerRef, CompilerRefDescriptor}

private object ScalaCompilerIndices {
  val backwardUsages: IndexId[CompilerRef, Seq[Int]]                        = IndexId.create("sc.back.refs")
  val backwardHierarchy: IndexId[CompilerRef, util.Collection[CompilerRef]] = JavaCompilerIndices.BACK_HIERARCHY

  val getIndices: util.Collection[IndexExtension[_, _, _ >: ClassfileData]] = util.Arrays.asList(
    backUsagesExtension,
    JavaCompilerIndices.createBackwardHierarchyExtension()
  )

  private[this] def backUsagesExtension: IndexExtension[CompilerRef, Seq[Int], ClassfileData] =
    new IndexExtension[CompilerRef, Seq[Int], ClassfileData] {
      override def getVersion: Int                                               = 0
      override def getName: IndexId[CompilerRef, Seq[Int]]                       = backwardUsages
      override def getIndexer: DataIndexer[CompilerRef, Seq[Int], ClassfileData] = _.backwardsReferences
      override def getKeyDescriptor: KeyDescriptor[CompilerRef]                  = CompilerRefDescriptor.INSTANCE
      override def getValueExternalizer: DataExternalizer[Seq[Int]]              = integerSeqExternalizer
    }

  private[this] def integerSeqExternalizer: DataExternalizer[Seq[Int]] = new DataExternalizer[Seq[Int]] {
    override def read(in: DataInput): Seq[Int] = ioutil.readSeq(in, () => ioutil.readINT(in)).asScala

    override def save(out: DataOutput, xs: Seq[Int]): Unit =
      ioutil.writeSeq(out, xs.asJava, ioutil.writeINT(out, _: Int))
  }
}
