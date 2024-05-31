package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.surroundWith.ScalaModCommandSurrounder
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * Surrounds an expression and return an expression
 */
abstract class ScalaExpressionSurrounder extends ScalaModCommandSurrounder {

  import ScalaTokenTypes.{COMMENTS_TOKEN_SET, tSEMICOLON => Semicolon}

  protected def isApplicableToMultipleElements: Boolean = false

  protected def isApplicableToUnitExpressions: Boolean = isApplicableToMultipleElements

  def isApplicable(element: PsiElement): Boolean = element match {
    case e: ScExpression =>
      val typeResult = e.`type`()
      typeResult.forall(tpe => !tpe.isUnit || isApplicableToUnitExpressions)
    case _: PsiWhiteSpace |
         _: ScValueOrVariable |
         _: ScFunction |
         _: ScTypeAlias => isApplicableToMultipleElements
    case _ if isApplicableToMultipleElements =>
      if (ScalaPsiUtil.isLineTerminator(element)) true
      else {
        val elementType = element.elementType
        elementType == Semicolon || COMMENTS_TOKEN_SET.contains(elementType)
      }
    case _ => false
  }

  override def isApplicable(elements: Array[PsiElement]): Boolean =
    if (elements.isEmpty) false
    else if (isApplicableToMultipleElements || elements.sizeIs == 1) elements.forall(isApplicable)
    else false

  override protected def surroundElements(elements: Array[PsiElement], context: ActionContext): Option[TextRange] =
    getSurroundSelectionRange(surroundedNode(elements))

  def surroundedNode(elements: Array[PsiElement]): ASTNode = {
    val result = surroundPsi(elements).getNode
    var childNode: ASTNode = null

    for {
      child <- elements
      nextNode = child.getNode
      parentNode = nextNode.getTreeParent
    } {
      val flag = childNode == null
      childNode = nextNode

      if (flag) parentNode.replaceChild(childNode, result)
      else parentNode.removeChild(childNode)
    }

    result
  }

  protected def needParenthesis(element: PsiElement): Boolean = element.getParent match {
    case _: ScSugarCallExpr |
         _: ScReferenceExpression => true
    case _ => false
  }

  protected final def surroundPsi(elements: Array[PsiElement]): ScExpression = {
    val element = elements.head

    val text = getTemplateAsString(elements)
      .parenthesize(needParenthesis = elements.length == 1 && this.needParenthesis(element))

    implicit val context: ProjectContext = element.projectContext
    ScalaPsiElementFactory.createExpressionFromText(text, element)
  }

  def getTemplateAsString(elements: Array[PsiElement]): String =
    elements.map(_.getNode.getText).mkString

  def getSurroundSelectionRange(node: ASTNode): Option[TextRange]

  protected def unwrapParenthesis(node: ASTNode): Option[PsiElement] = node.getPsi match {
    case p: ScParenthesisedExpr => p.innerElement
    case e => Option(e)
  }

  protected final def isBooleanExpression(element: PsiElement): Boolean = element match {
    case expr: ScExpression => expr.getTypeIgnoreBaseType.getOrAny.conforms(api.Boolean(expr.getProject))
    case _ => false
  }
}
