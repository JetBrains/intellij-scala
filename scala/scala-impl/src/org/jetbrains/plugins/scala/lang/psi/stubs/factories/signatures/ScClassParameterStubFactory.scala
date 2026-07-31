package org.jetbrains.plugins.scala.lang.psi.stubs.factories.signatures

import com.intellij.psi.stubs.IndexSink
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.ScClassParameterElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.IndexSinkExt
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY

final class ScClassParameterStubFactory(elementType: ScClassParameterElementType) extends ScParamStubFactory(elementType) {
  override def createPsi(stub: ScParameterStub): ScClassParameter = new ScClassParameterImpl(stub)

  override def indexStub(stub: ScParameterStub, sink: IndexSink): Unit = {
    sink.occurrences(CLASS_PARAMETER_NAME_KEY, stub.getName)
    stub.indexImplicits(sink)
  }
}
