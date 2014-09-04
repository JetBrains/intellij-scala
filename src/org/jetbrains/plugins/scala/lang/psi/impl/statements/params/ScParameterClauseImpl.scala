package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub


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

  def parameters: Seq[ScParameter] =
    getStubOrPsiChildren[ScParameter](TokenSets.PARAMETERS, JavaArrayFactoryUtil.ScParameterFactory)

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