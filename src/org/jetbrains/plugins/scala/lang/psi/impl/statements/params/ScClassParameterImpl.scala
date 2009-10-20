package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import stubs.elements.wrappers.DummyASTNode
import stubs.{ScParameterStub}

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScClassParameterImpl(node: ASTNode) extends ScParameterImpl(node) with ScClassParameter {

  def this(stub: ScParameterStub) = {
    this(DummyASTNode)
    setStub(stub)
    setNode(null)
  }

  override def toString: String = "ClassParameter"

  def isVal() = findChildByType(ScalaTokenTypes.kVAL) != null
  def isVar() = findChildByType(ScalaTokenTypes.kVAR) != null

  override def isStable: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isStable
    }
    return !isVar
  }
}