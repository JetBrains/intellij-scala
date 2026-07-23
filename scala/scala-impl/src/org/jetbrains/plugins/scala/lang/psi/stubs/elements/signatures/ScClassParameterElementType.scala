package org.jetbrains.plugins.scala.lang.psi.stubs.elements
package signatures

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY

final class ScClassParameterElementType extends ScParamElementType[ScClassParameter](ScClassParameterElementType.DebugName) {
  override def createElement(node: ASTNode): ScClassParameter = new ScClassParameterImpl(node)
}

object ScClassParameterElementType {
  val DebugName = "class parameter"
}

class ScClassParameterStubFactory(elementType: IElementType) extends ScParamStubFactory(elementType) {
  override def createPsi(stub: ScParameterStub): ScClassParameter = new ScClassParameterImpl(stub)

  override def indexStub(stub: ScParameterStub, sink: IndexSink): Unit = {
    sink.occurrences(CLASS_PARAMETER_NAME_KEY, stub.getName)
    stub.indexImplicits(sink)
  }

  override def getExternalId: String = s"scala.${ScClassParameterElementType.DebugName}"
}
