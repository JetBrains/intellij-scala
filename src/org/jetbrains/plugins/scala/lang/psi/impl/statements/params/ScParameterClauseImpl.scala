package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScParameterClauseImpl private(stub: ScParamClauseStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.PARAM_CLAUSE, node) with ScParameterClause {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScParamClauseStub) = this(stub, null)

  override def toString: String = "ParametersClause"

  def parameters: Seq[ScParameter] = {
    getStubOrPsiChildren[ScParameter](TokenSets.PARAMETERS, JavaArrayFactoryUtil.ScParameterFactory)
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  override def effectiveParameters: Seq[ScParameter] = {
    if (!isImplicit) return parameters
    //getParent is sufficient (not getContext), for synthetic clause, getParent will return other PSI,
    //which is ok, it will not add anything more
    getParent match {
      case clauses: ScParameters =>
        val element =
          clauses.getParent match {
            case f: ScFunction => f
            case p: ScPrimaryConstructor =>
              p.containingClass match {
                case c: ScClass => c
                case _ => return parameters
              }
            case _ => return parameters
          }

        val syntheticClause = ScalaPsiUtil.syntheticParamClause(element, clauses, element.isInstanceOf[ScClass], hasImplicit = false)
        syntheticClause match {
          case Some(sClause) =>
            val synthParameters = sClause.parameters
            synthParameters.foreach(_.setContext(this, null))
            synthParameters ++ parameters
          case _ => parameters
        }

      case _ => parameters
    }
  }

  def isImplicit: Boolean = byStubOrPsi(_.isImplicit)(findChildByType(ScalaTokenTypes.kIMPLICIT) != null)

  def addParameter(param: ScParameter): ScParameterClause = {
    val params = parameters
    val vararg =
      if (params.isEmpty) false
      else params.last.isRepeatedParameter
    val rParen = if (vararg) params.last.getNode else getLastChild.getNode

    val node = getNode

    def comma = createComma.getNode

    def space = createNewLineNode(" ")

    if (params.nonEmpty && !vararg) {
      node.addChild(comma, rParen)
      node.addChild(space, rParen)
    }
    node.addChild(param.getNode, rParen)
    if (vararg) {
      node.addChild(comma, rParen)
      node.addChild(space, rParen)
    }
    this
  }

  override def owner: PsiElement = {
    ScalaPsiUtil.getContextOfType(this, true, classOf[ScFunctionExpr], classOf[ScFunction], classOf[ScPrimaryConstructor])
  }
}
