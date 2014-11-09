package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures 

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import com.intellij.psi.stubs.IndexSink
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScClassParameterElementType extends ScParamElementType[ScClassParameter]("class parameter") {

  def createPsi(stub: ScParameterStub): ScClassParameter = {
    new ScClassParameterImpl(stub)
  }

  override def indexStub(stub: ScParameterStub, sink: IndexSink) {
    super.indexStub(stub, sink)

    val name = stub.getName
      if (name != null) {
        sink.occurrence(ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY, name)
      }
  }
}