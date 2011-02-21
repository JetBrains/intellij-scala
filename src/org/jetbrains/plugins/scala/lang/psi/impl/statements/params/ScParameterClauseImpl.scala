package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import stubs.elements.wrappers.DummyASTNode
import stubs.ScParamClauseStub;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._


/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScParameterClauseImpl extends ScalaStubBasedElementImpl[ScParameterClause] with ScParameterClause {

  def this(node: ASTNode) = {
    this (); setNode(node)
  }

  def this(stub: ScParamClauseStub) = {
    this (); setStub(stub); setNode(null)
  }

  override def toString: String = "ParametersClause"

  def parameters: Seq[ScParameter] = {
    getStubOrPsiChildren[ScParameter](TokenSets.PARAMETERS, new ArrayFactory[ScParameter] {
      def create(count: Int): Array[ScParameter] = new Array[ScParameter](count)
    })
  }

  def isImplicit: Boolean = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScParamClauseStub].isImplicit
    } else getNode.findChildByType(ScalaTokenTypes.kIMPLICIT) != null
  }

  def addParameter(param: ScParameter): ScParameterClause = {
    val params = parameters
    val vararg =
      if (params.length == 0) false
      else params(params.length - 1).isRepeatedParameter
    val rParen = if (vararg) params(params.length - 1).getNode else getLastChild.getNode
    val node = getNode
    if (params.length > 0 && !vararg) {
      val comma = ScalaPsiElementFactory.createComma(getManager).getNode
      val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
      node.addChild(comma, rParen)
      node.addChild(space, rParen)
    }
    node.addChild(param.getNode, rParen)
    if (vararg) {
      val comma = ScalaPsiElementFactory.createComma(getManager).getNode
      val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
      node.addChild(comma, rParen)
      node.addChild(space, rParen)
    }
    return this
  }
}