package org.jetbrains.plugins.scala.lang.psi.stubs.elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParametersImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClausesStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParamClausesStubImpl

class ScParamClausesElementType extends ScalaStubBasedElementType[ScParamClausesStub, ScParameters](ScParamClausesElementType.DebugName) {
  override def createElement(node: ASTNode): ScParameters = new ScParametersImpl(node)
}

object ScParamClausesElementType {
  val DebugName = "parameter clauses"
}

class ScParamClausesStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScParamClausesStub, ScParameters] {

  override def serialize(stub: ScParamClausesStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClausesStub =
    new ScParamClausesStubImpl(parentStub, elementType)

  override def createStub(psi: ScParameters, parentStub: StubElement[_ <: PsiElement]): ScParamClausesStub =
    ScStubElementType.Processing.run {
      new ScParamClausesStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScParamClausesStub): ScParameters = new ScParametersImpl(stub)

  override def indexStub(stub: ScParamClausesStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScParamClausesElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
