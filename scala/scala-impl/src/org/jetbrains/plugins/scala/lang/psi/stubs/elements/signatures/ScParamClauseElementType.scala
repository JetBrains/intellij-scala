package org.jetbrains.plugins.scala.lang.psi.stubs.elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParamClauseStubImpl

class ScParamClauseElementType extends ScalaStubBasedElementType[ScParamClauseStub, ScParameterClause](ScParamClauseElementType.DebugName) {
  override def createElement(node: ASTNode): ScParameterClause = new ScParameterClauseImpl(node)
}

object ScParamClauseElementType {
  val DebugName = "parameter clause"
}

class ScParamClauseStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScParamClauseStub, ScParameterClause] {

  override def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.hasImplicitKeyword)
    dataStream.writeBoolean(stub.hasUsingKeyword)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, elementType,
      hasImplicitKeyword = dataStream.readBoolean,
      hasUsingKeyword = dataStream.readBoolean,
    )

  override def createStub(parameterClause: ScParameterClause, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    ScStubElementType.Processing.run {
      new ScParamClauseStubImpl(parentStub, elementType,
        hasImplicitKeyword = parameterClause.hasImplicitKeyword,
        hasUsingKeyword = parameterClause.hasUsingKeyword,
      )
    }

  override def createPsi(stub: ScParamClauseStub): ScParameterClause = new ScParameterClauseImpl(stub)

  override def indexStub(stub: ScParamClauseStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScParamClauseElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
