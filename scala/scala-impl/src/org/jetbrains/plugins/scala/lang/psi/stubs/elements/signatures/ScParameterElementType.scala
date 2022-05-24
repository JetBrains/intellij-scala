package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParameterElementType extends ScParamElementType("parameter") {
  override def createElement(node: ASTNode): ScParameter = new ScParameterImpl(node)

  override def createPsi(stub: ScParameterStub): ScParameter = new ScParameterImpl(stub)
}