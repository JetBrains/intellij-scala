package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamClauseStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTypeParamClauseElementType[Func <: ScTypeParamClause]
        extends ScStubElementType[ScTypeParamClauseStub, ScTypeParamClause]("type parameter clause") {
  def serialize(stub: ScTypeParamClauseStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getTypeParamClauseText)
  }

  override def createElement(node: ASTNode): ScTypeParamClause = new ScTypeParamClauseImpl(node)

  override def createPsi(stub: ScTypeParamClauseStub): ScTypeParamClause = new ScTypeParamClauseImpl(stub)

  def createStubImpl[ParentPsi <: PsiElement](psi: ScTypeParamClause, parentStub: StubElement[ParentPsi]): ScTypeParamClauseStub = {
    new ScTypeParamClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, psi.getText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeParamClauseStub = {
    val text = dataStream.readName().toString
    new ScTypeParamClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, text)
  }
}