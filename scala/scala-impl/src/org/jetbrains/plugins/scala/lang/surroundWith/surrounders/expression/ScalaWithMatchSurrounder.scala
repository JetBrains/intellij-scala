package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

object ScalaWithMatchSurrounder extends ScalaExpressionSurrounder {

  import ScalaPsiUtil.functionArrow

  override def isApplicable(element: PsiElement): Boolean = element match {
    case _: ScBlockExpr => true
    case _: ScBlock => false
    case _: ScExpression | _: PsiWhiteSpace =>
      super.isApplicable(element)
    case _ => false
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

  override protected val isApplicableToUnitExpressions: Boolean = true

  override def getSurroundSelectionRange(withMatchNode: ASTNode): Option[TextRange] =
    unwrapParenthesis(withMatchNode) match {
      case Some(stmt: ScMatch) =>
        getRange(stmt.toIndentationBasedSyntax)
      case _ => None
    }

  private def getRange(stmt: ScMatch): Option[TextRange] = for {
    clauses    <- stmt.caseClauses
    clause     <- clauses.caseClauses.headOption
    pattern    <- clause.pattern.flatMap(_.forcePostprocessAndRestore)
    patternNode = pattern.getNode if patternNode != null
    offset      = patternNode.getTextRange.getStartOffset
    _           = patternNode.getTreeParent.removeChild(patternNode)
  } yield new TextRange(offset, offset)
}
