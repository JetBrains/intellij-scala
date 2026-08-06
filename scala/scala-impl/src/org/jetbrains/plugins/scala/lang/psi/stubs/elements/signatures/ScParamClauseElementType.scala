package org.jetbrains.plugins.scala.lang.psi.stubs.elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParamClauseStubImpl

class ScParamClauseElementType extends ScStubElementType[ScParamClauseStub, ScParameterClause]("parameter clause") {
  override def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.hasImplicitKeyword)
    dataStream.writeBoolean(stub.hasUsingKeyword)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, this,
      hasImplicitKeyword = dataStream.readBoolean,
      hasUsingKeyword = dataStream.readBoolean,
    )

  override def createStubImpl(parameterClause: ScParameterClause, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, this,
      hasImplicitKeyword = parameterClause.hasImplicitKeyword,
      hasUsingKeyword = parameterClause.hasUsingKeyword,
    )

  override def createElement(node: ASTNode): ScParameterClause = new ScParameterClauseImpl(node)

  override def createPsi(stub: ScParamClauseStub): ScParameterClause = new ScParameterClauseImpl(stub)
}