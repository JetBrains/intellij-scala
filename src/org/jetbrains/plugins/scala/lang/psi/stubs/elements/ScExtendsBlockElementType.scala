package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.SUPER_CLASS_NAME_KEY

/**
  * @author ilyas
  */
class ScExtendsBlockElementType
  extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock]("extends block") {

  override def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeInt(stub.getBaseClasses.length)
    for (name <- stub.getBaseClasses) dataStream.writeName(name)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub = {
    val n = dataStream.readInt
    val baseClasses = new Array[StringRef](n)
    for (i <- 0 until n) baseClasses(i) = dataStream.readName
    new ScExtendsBlockStubImpl(parentStub, this, baseClasses)
  }

  override def createStub(psi: ScExtendsBlock, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub =
    new ScExtendsBlockStubImpl(parentStub, this, psi.directSupersNames.toArray)

  override def indexStub(stub: ScExtendsBlockStub, sink: IndexSink): Unit =
    this.indexStub(stub.getBaseClasses, sink, SUPER_CLASS_NAME_KEY)

  override def createElement(node: ASTNode): ScExtendsBlock = new ScExtendsBlockImpl(node)

  override def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = new ScExtendsBlockImpl(stub)
}


