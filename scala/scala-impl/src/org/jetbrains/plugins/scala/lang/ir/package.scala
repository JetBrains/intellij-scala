package org.jetbrains.plugins.scala.lang

import com.intellij.psi.stubs.{StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.ir.typeTree.{TypeTree, TypeTreeHolder}

package object ir {
  implicit class StubOutputStreamForIRExt(private val dataStream: StubOutputStream) extends AnyVal {
    def writeTypeTreeHolderOption(tree: Option[TypeTreeHolder]): Unit = ???
    def writeTypeTreeHolder(tree: TypeTreeHolder): Unit = ???
    def writeTypeTreeHolders(trees: Seq[TypeTreeHolder]): Unit = ???
  }

  implicit class StubInputStreamForIRExt(private val dataStream: StubInputStream) extends AnyVal {
    def readTypeTreeHolderOption(): Option[TypeTreeHolder] = ???
    def readTypeTreeHolder(): TypeTreeHolder = ???
    def readTypeTreeHolders(): Seq[TypeTreeHolder] = ???
  }
}
