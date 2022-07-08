package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScFieldIdImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFieldIdStubImpl

class ScFieldIdElementType extends ScStubElementType[ScFieldIdStub, ScFieldId]("field id") {
  override def serialize(stub: ScFieldIdStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScFieldIdStub =
    new ScFieldIdStubImpl(parentStub, this, name = dataStream.readNameString())

  override def createStubImpl(psi: ScFieldId, parentStub: StubElement[_ <: PsiElement]): ScFieldIdStub =
    new ScFieldIdStubImpl(parentStub, this, name = psi.name)

  override def createElement(node: ASTNode): ScFieldId = new ScFieldIdImpl(node)

  override def createPsi(stub: ScFieldIdStub): ScFieldId = new ScFieldIdImpl(stub)
}