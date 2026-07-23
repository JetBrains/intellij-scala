package org.jetbrains.plugins.scala.lang.psi.stubs.elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub

class ScParameterElementType extends ScParamElementType[ScParameter](ScParameterElementType.DebugName) {
  override def createElement(node: ASTNode): ScParameter = new ScParameterImpl(node)
}

object ScParameterElementType {
  val DebugName = "parameter"
}

class ScParameterStubFactory(elementType: IElementType) extends ScParamStubFactory(elementType) {

  override def createPsi(stub: ScParameterStub): ScParameter = new ScParameterImpl(stub)

  override def indexStub(stub: ScParameterStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScParameterElementType.DebugName}"
}
