package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.TYPE_LAMBDA
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamClauseStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamClauseStubImpl

class ScTypeParamClauseElementType
  extends ScStubElementType[ScTypeParamClauseStub, ScTypeParamClause]("type parameter clause") {

  override def shouldCreateStub(node: ASTNode): Boolean = {
    val parent = node.getTreeParent
    (parent == null || parent.getElementType != TYPE_LAMBDA) &&
      super.shouldCreateStub(node)
  }

  override def serialize(stub: ScTypeParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.typeParameterClauseText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeParamClauseStub =
    new ScTypeParamClauseStubImpl(parentStub, this, dataStream.readNameString)

  override def createStubImpl(typeParamClause: ScTypeParamClause, parentStub: StubElement[_ <: PsiElement]): ScTypeParamClauseStub =
    new ScTypeParamClauseStubImpl(parentStub, this, typeParamClause.getText)

  override def createElement(node: ASTNode): ScTypeParamClause = new ScTypeParamClauseImpl(node)

  override def createPsi(stub: ScTypeParamClauseStub): ScTypeParamClause = new ScTypeParamClauseImpl(stub)
}