package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScAnonymousGivenParameterClause, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScAnonymousGivenParameterClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParamClauseStubImpl

class ScAnonymousGivenParamClauseElementType
  extends ScStubElementType[ScParamClauseStub, ScParameterClause]("anonymous given parameter clause") {

  override def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeBoolean(stub.isGiven)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, this,
      isImplicit = dataStream.readBoolean(),
      isGiven = dataStream.readBoolean())

  override def createStubImpl(parameterClause: ScParameterClause, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, this,
      isImplicit = parameterClause.isImplicit,
      isGiven = parameterClause.isGiven)

  override def createElement(node: ASTNode): ScAnonymousGivenParameterClause = new ScAnonymousGivenParameterClauseImpl(node)

  override def createPsi(stub: ScParamClauseStub): ScAnonymousGivenParameterClauseImpl = new ScAnonymousGivenParameterClauseImpl(stub)
}