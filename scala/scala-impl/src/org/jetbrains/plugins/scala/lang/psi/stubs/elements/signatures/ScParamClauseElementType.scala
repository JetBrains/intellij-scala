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

class ScParamClauseElementType extends ScStubElementType[ScParamClauseStub, ScParameterClause]("parameter clause") {
  override def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeBoolean(stub.isUsing)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, this,
      isImplicit = dataStream.readBoolean,
      isUsing = dataStream.readBoolean,
    )

  override def createStubImpl(parameterClause: ScParameterClause, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, this,
      isImplicit = parameterClause.isImplicit,
      isUsing = parameterClause.isUsing,
    )

  override def createElement(node: ASTNode): ScParameterClause = new ScParameterClauseImpl(node)

  override def createPsi(stub: ScParamClauseStub): ScParameterClause = new ScParameterClauseImpl(stub)
}