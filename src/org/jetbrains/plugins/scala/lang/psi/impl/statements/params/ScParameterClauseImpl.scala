package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
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

  def parameters: Seq[ScParameter] = {
    getStubOrPsiChildren[ScParameter](TokenSets.PARAMETERS, JavaArrayFactoryUtil.ScParameterFactory)
  }

  @volatile
  private var synthClause: Option[ScParameterClause] = None
  @volatile
  private var synthClauseModCount: Long = -1
  private val SYNTH_LOCK = new Object()

  override def effectiveParameters: Seq[ScParameter] = {
    if (!isImplicit) return parameters
    //getParent is sufficient (not getContext), for synthetic clause, getParent will return other PSI,
    //which is ok, it will not add anything more
    getParent match {
      case clauses: ScParameters =>
        val typeParametersOwner: ScTypeParametersOwner =
          clauses.getParent match {
            case f: ScFunction => f
            case p: ScPrimaryConstructor =>
              p.containingClass match {
                case c: ScClass => c
                case _ => return parameters
              }
            case _ => return parameters
          }
        def syntheticClause(): Option[ScParameterClause] = {
          val modCount = getManager.getModificationTracker.getModificationCount
          if (synthClauseModCount == modCount) return synthClause
          SYNTH_LOCK synchronized { //it's important for all calculations to have the same psi here
            if (synthClauseModCount == modCount) return synthClause
            synthClause = ScalaPsiUtil.syntheticParamClause(typeParametersOwner, clauses,
              typeParametersOwner.isInstanceOf[ScClass])
            synthClauseModCount = modCount
            synthClause
          }
        }
        syntheticClause() match {
          case Some(sClause) =>
            val synthParameters = sClause.parameters
            synthParameters.foreach(_.setContext(this, null))
            synthParameters ++ parameters
          case _ => parameters
        }

      case _ => parameters
    }
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
    this
  }
}