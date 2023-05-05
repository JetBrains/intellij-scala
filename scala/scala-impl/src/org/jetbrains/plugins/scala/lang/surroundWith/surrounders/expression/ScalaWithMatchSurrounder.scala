package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

object ScalaWithMatchSurrounder extends ScalaExpressionSurrounder {

  import ScalaPsiUtil.{functionArrow, isLineTerminator}

  override def isApplicable(elements: Array[PsiElement]): Boolean =
    elements.length <= 1 && super.isApplicable(elements)

  override def isApplicable(element: PsiElement): Boolean = element match {
    case _: ScBlockExpr => true //TODO perhaps this is a temporary hack?
    case block: ScBlock =>
      !block.hasRBrace && (block.exprs match {
        case Seq(_: ScExpression) => true
        case _ => false
      })
    case _: ScExpression | _: PsiWhiteSpace => true
    case _ => isLineTerminator(element)
  }

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val needParenthesis = elements match {
      case Array(_: ScDo |
                 _: ScIf |
                 _: ScTry |
                 _: ScFor |
                 _: ScWhile |
                 _: ScThrow |
                 _: ScReturn |
                 _: ScMatch) => true
      case Array(_) => false
      case _ => true
    }

    val prefix = super.getTemplateAsString(elements).parenthesize(needParenthesis)

    implicit val project: Project = elements.headOption.map(_.getProject).orNull
    s"""$prefix match {
       |case a  $functionArrow
       |}""".stripMargin
  }

  //noinspection ReferencePassedToNls
  override def getTemplateDescription: String = ScalaKeyword.MATCH

  override def getSurroundSelectionRange(editor: Editor, withMatchNode: ASTNode): TextRange = {
    val matchExpr = unwrapParenthesis(withMatchNode) match {
      case Some(stmt: ScMatch) => stmt.toIndentationBasedSyntax
      case _ => return null
    }

    val patternNode = matchExpr.caseClauses
      .flatMap(_.caseClauses.headOption)
      .flatMap(_.pattern)
      .map(_.getNode)
      .getOrElse(return null)
    val offset = patternNode.getTextRange.getStartOffset
    patternNode.getTreeParent.removeChild(patternNode)

    new TextRange(offset, offset)
  }
}
