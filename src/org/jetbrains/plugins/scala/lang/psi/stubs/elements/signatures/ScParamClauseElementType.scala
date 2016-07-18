package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParamClauseStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */

class ScParamClauseElementType
  extends ScStubElementType[ScParamClauseStub, ScParameterClause]("parameter clause") {
  def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream) {
    dataStream.writeBoolean(stub.isImplicit)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub = {
    val implic = dataStream.readBoolean
    new ScParamClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, implic)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScParameterClause, parentStub: StubElement[ParentPsi]): ScParamClauseStubImpl[ParentPsi] = {
    new ScParamClauseStubImpl(parentStub, this, psi.isImplicit)
  }

  override def createPsi(stub: ScParamClauseStub): ScParameterClause = new ScParameterClauseImpl(stub)

  override def createElement(node: ASTNode): ScParameterClause = new ScParameterClauseImpl(node)
}