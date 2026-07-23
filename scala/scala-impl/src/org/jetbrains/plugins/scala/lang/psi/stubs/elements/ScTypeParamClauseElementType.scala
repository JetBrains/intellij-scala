package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.TYPE_LAMBDA
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamClauseStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamClauseStubImpl

class ScTypeParamClauseElementType
  extends ScalaStubBasedElementType[ScTypeParamClauseStub, ScTypeParamClause](ScTypeParamClauseElementType.DebugName) {

  override def createElement(node: ASTNode): ScTypeParamClause = new ScTypeParamClauseImpl(node)
}

object ScTypeParamClauseElementType {
  val DebugName = "type parameter clause"
}

class ScTypeParamClauseStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScTypeParamClauseStub, ScTypeParamClause] {

  override def shouldCreateStub(node: ASTNode): Boolean = {
    val parent = node.getTreeParent
    (parent == null || parent.getElementType != TYPE_LAMBDA) &&
      !ScStubElementType.isLocal(node)
  }

  override def serialize(stub: ScTypeParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.typeParameterClauseText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeParamClauseStub =
    new ScTypeParamClauseStubImpl(parentStub, elementType, dataStream.readNameString)

  override def createStub(typeParamClause: ScTypeParamClause, parentStub: StubElement[_ <: PsiElement]): ScTypeParamClauseStub =
    ScStubElementType.Processing.run {
      new ScTypeParamClauseStubImpl(parentStub, elementType, typeParamClause.getText)
    }

  override def createPsi(stub: ScTypeParamClauseStub): ScTypeParamClause = new ScTypeParamClauseImpl(stub)

  override def indexStub(stub: ScTypeParamClauseStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScTypeParamClauseElementType.DebugName}"
}
