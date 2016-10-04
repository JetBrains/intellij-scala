package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAccessModifierStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScAccessModifierElementType extends ScStubElementType[ScAccessModifierStub, ScAccessModifier]("access modifier") {
  override def serialize(stub: ScAccessModifierStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isProtected)
    dataStream.writeBoolean(stub.isPrivate)
    dataStream.writeBoolean(stub.isThis)
    dataStream.writeOptionName(stub.idText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAccessModifierStub =
    new ScAccessModifierStubImpl(parentStub, this,
      isProtected = dataStream.readBoolean,
      isPrivate = dataStream.readBoolean,
      isThis = dataStream.readBoolean,
      idTextRef = dataStream.readOptionName)

  override def createStub(modifier: ScAccessModifier, parentStub: StubElement[_ <: PsiElement]): ScAccessModifierStub =
    new ScAccessModifierStubImpl(parentStub, this,
      isProtected = modifier.isProtected,
      isPrivate = modifier.isPrivate,
      isThis = modifier.isThis,
      idTextRef = modifier.idText.asReference)

  override def createElement(node: ASTNode): ScAccessModifier = new ScAccessModifierImpl(node)

  override def createPsi(stub: ScAccessModifierStub): ScAccessModifier = new ScAccessModifierImpl(stub)
}