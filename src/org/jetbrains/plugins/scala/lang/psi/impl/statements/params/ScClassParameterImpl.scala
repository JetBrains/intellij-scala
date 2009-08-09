package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import stubs.elements.wrappers.DummyASTNode
import stubs.{ScParameterStub}

import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base._


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