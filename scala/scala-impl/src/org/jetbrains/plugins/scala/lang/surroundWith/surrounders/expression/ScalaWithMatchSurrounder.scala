package org.jetbrains.plugins.scala.lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author AlexanderPodkhalyuzin
  *         Date: 28.04.2008
  */
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
                 _: ScReturn) => true
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

  override def getSurroundSelectionRange(withMatchNode: ASTNode): TextRange = {
    val element = withMatchNode.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val patternNode = element.getNode.getLastChildNode.getTreePrev.getTreePrev.getFirstChildNode.getFirstChildNode.getTreeNext.getTreeNext
    val offset = patternNode.getTextRange.getStartOffset
    patternNode.getTreeParent.removeChild(patternNode)

    new TextRange(offset, offset)
  }
}
