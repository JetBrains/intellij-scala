package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, ModCount}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScParameterClauseImpl private(stub: ScParamClauseStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.PARAM_CLAUSE, node) with ScParameterClause {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScParamClauseStub) = this(stub, null)

  override def toString: String = "ParametersClause"

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  def parameters: Seq[ScParameter] = {
    getStubOrPsiChildren[ScParameter](TokenSets.PARAMETERS, JavaArrayFactoryUtil.ScParameterFactory).toSeq
  }

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  override def effectiveParameters: Seq[ScParameter] = {
    if (!isImplicit) return parameters

    //getParent is sufficient (not getContext), for synthetic clause, getParent will return other PSI,
    //which is ok, it will not add anything more
    val maybeSyntheticClause = getParent match {
      case clauses: ScParameters =>
        val maybeOwner = clauses.getParent match {
          case f: ScFunction => Some((f, false))
          case p: ScPrimaryConstructor =>
            p.containingClass match {
              case c: ScClass => Some((c, true))
              case _ => None
            }
          case _ => None
        }
        maybeOwner.flatMap {
          case (owner, isClassParameter) =>
            ScalaPsiUtil.syntheticParamClause(owner, clauses, isClassParameter)(hasImplicit = false)
        }
      case _ => None
    }

    val syntheticParameters = maybeSyntheticClause.toSeq.flatMap(_.parameters)
    syntheticParameters.foreach {
      _.setContext(this, null)
    }

    syntheticParameters ++ parameters
  }

  @Cached(ModCount.anyScalaPsiModificationCount, this)
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
