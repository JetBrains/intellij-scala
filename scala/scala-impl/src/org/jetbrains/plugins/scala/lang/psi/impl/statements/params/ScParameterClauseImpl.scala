package org.jetbrains.plugins.scala.lang.psi.impl.statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker, cached, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScModifierList, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGivenDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub

class ScParameterClauseImpl private(stub: ScParamClauseStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.PARAM_CLAUSE, node) with ScParameterClause {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScParamClauseStub) = this(stub, null)

  override def toString: String = "ParametersClause"

  override def parameters: Seq[ScParameter] = _parameters()

  private val _parameters = cached("parameters", ModTracker.anyScalaPsiChange, () => {
    getStubOrPsiChildren[ScParameter](TokenSets.PARAMETERS, JavaArrayFactoryUtil.ScParameterFactory).toSeq
  })

  override def effectiveParameters: Seq[ScParameter] =
    cachedInUserData("effectiveParameters", this, BlockModificationTracker(this)) {
      if (isImplicitOrUsing) {
        val syntheticParameters = getSyntheticParameters
        syntheticParameters ++ parameters
      } else
        parameters
    }

  private def getSyntheticParameters: Seq[ScParameter] = {
    //getParent is sufficient (not getContext), for synthetic clause, getParent will return other PSI,
    //which is ok, it will not add anything more
    val maybeSyntheticClause = getParent match {
      case clauses: ScParameters =>
        val maybeOwner = clauses.getParent match {
          case f: ScFunction => Some((f, false))
          case p: ScPrimaryConstructor =>
            p.containingClass match {
              case c: ScClass => Some((c, true))
              case _          => None
            }
          case _ => None
        }

        maybeOwner.flatMap {
          case (owner, isClassParameter) =>
            ScalaPsiUtil.syntheticParamClause(
              owner.typeParameters,
              clauses,
              isClassParameter,
              hasImplicit = false
            )
        }
      case _ => None
    }

    val syntheticParameters = maybeSyntheticClause.toSeq.flatMap(_.parameters)
    syntheticParameters.foreach {
      _.context = this
    }
    syntheticParameters
  }

  override def hasParenthesis: Boolean =
    getFirstChild.elementType == ScalaTokenTypes.tLPARENTHESIS &&
      getLastChild.elementType == ScalaTokenTypes.tRPARENTHESIS

  override def isImplicit: Boolean = _isImplicit()

  private val _isImplicit = cached("isImplicit", ModTracker.anyScalaPsiChange, () => {
    import ScModifierList._

    def hasImplicitKeyword =
      findChildByType(ScalaTokenTypes.kIMPLICIT) != null ||
        findChild[ScClassParameter]
          .exists(_.getModifierList.isImplicit)

    byStubOrPsi(_.isImplicit)(hasImplicitKeyword)
  })

  override def isUsing: Boolean = _isUsing()

  private val _isUsing = cached("isUsing", ModTracker.anyScalaPsiChange, () => {
    def hasUsingKeyword =
      findChildByType(ScalaTokenType.UsingKeyword) != null

    byStubOrPsi(_.isUsing)(hasUsingKeyword)
  })

  override def addParameter(param: ScParameter): ScParameterClause = {
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

  override def deleteChildInternal(child: ASTNode): Unit = {
    val parameters = this.parameters
    val isParameter = parameters.exists(_.getNode == child)
    def childIsLastParameterToBeDeleted = parameters.length == 1 && isParameter
    def isSingleParameterClause =
      !this.getPrevSiblingNotWhitespaceComment.is[ScParameterClause] &&
        !this.getNextSiblingNotWhitespaceComment.is[ScParameterClause]

    if (childIsLastParameterToBeDeleted && !isSingleParameterClause) {
      this.delete()
    } else if (isParameter) {
      if (childIsLastParameterToBeDeleted) {
        val prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev)
        if (prev.hasElementType(ScalaTokenTypes.kIMPLICIT) || prev.hasElementType(ScalaTokenType.UsingKeyword)) {
          deleteChildInternal(prev)
        }
      }
      ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
    } else {
      super.deleteChildInternal(child)
    }
  }

  override def owner: PsiElement =
    PsiTreeUtil.getContextOfType(
      this,
      true,
      classOf[ScFunctionExpr],
      classOf[ScFunction],
      classOf[ScPrimaryConstructor],
      classOf[ScGivenDefinition],
      classOf[ScExtension]
    )
}
