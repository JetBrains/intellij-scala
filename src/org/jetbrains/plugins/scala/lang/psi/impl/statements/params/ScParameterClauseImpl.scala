package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScParameterClauseImpl private(stub: StubElement[ScParameterClause], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScParameterClause {

  def this(node: ASTNode) =
    this(null, null, node)

  def this(stub: ScParamClauseStub) =
    this(stub, ScalaElementTypes.PARAM_CLAUSE, null)

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

        def syntheticClause(): Option[ScParameterClause] = {
          val modCount = getManager.getModificationTracker.getModificationCount
          if (synthClauseModCount == modCount) return synthClause
          SYNTH_LOCK synchronized {
            //it's important for all calculations to have the same psi here
            if (synthClauseModCount == modCount) return synthClause
            synthClause = ScalaPsiUtil.syntheticParamClause(element, clauses,
              element.isInstanceOf[ScClass], hasImplicit = false)
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
      return stub.asInstanceOf[ScParamClauseStub].isImplicit
    }
    getNode.findChildByType(ScalaTokenTypes.kIMPLICIT) != null
  }

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
