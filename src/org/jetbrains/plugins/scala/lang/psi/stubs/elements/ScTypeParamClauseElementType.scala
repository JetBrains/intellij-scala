package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef.fromString
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamClauseStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScTypeParamClauseElementType
  extends ScStubElementType[ScTypeParamClauseStub, ScTypeParamClause]("type parameter clause") {

  override def serialize(stub: ScTypeParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.typeParameterClauseText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeParamClauseStub =
    new ScTypeParamClauseStubImpl(parentStub, this,
      typeParameterClauseTextRef = dataStream.readName)

  override def createStub(typeParamClause: ScTypeParamClause, parentStub: StubElement[_ <: PsiElement]): ScTypeParamClauseStub =
    new ScTypeParamClauseStubImpl(parentStub, this,
      typeParameterClauseTextRef = fromString(typeParamClause.getText))

  override def createElement(node: ASTNode): ScTypeParamClause = new ScTypeParamClauseImpl(node)

  override def createPsi(stub: ScTypeParamClauseStub): ScTypeParamClause = new ScTypeParamClauseImpl(stub)
}