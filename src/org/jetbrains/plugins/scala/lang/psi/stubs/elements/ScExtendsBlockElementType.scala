package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.SUPER_CLASS_NAME_KEY

/**
  * @author ilyas
  */
class ScExtendsBlockElementType extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock]("extends block") {

  override def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.baseClasses)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub = {
    new ScExtendsBlockStubImpl(parentStub, this,
      baseClassesRefs = dataStream.readNames)
  }

  override def createStub(block: ScExtendsBlock, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub =
    new ScExtendsBlockStubImpl(parentStub, this,
      baseClassesRefs = block.directSupersNames.toArray.asReferences)

  override def indexStub(stub: ScExtendsBlockStub, sink: IndexSink): Unit =
    this.indexStub(stub.baseClasses, sink, SUPER_CLASS_NAME_KEY)

  override def createElement(node: ASTNode): ScExtendsBlock = new ScExtendsBlockImpl(node)

  override def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = new ScExtendsBlockImpl(stub)
}


