package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParameterElementType extends ScParamElementType[ScParameter]("parameter") {


  def createPsi(stub: ScParameterStub): ScParameter = {
    new ScParameterImpl(stub)
  }
}