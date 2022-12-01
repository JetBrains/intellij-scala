package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/*
 * Surrounds expression with do - while: do { Expression } while { <Cursor> }
 */
class ScalaWithDoWhileSurrounder extends ScalaExpressionSurrounder {

  // do-while is not available in Scala 3
  override def isApplicable(elements: Array[PsiElement]): Boolean =
    elements.nonEmpty && !elements.head.isInScala3File && super.isApplicable(elements)

  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "do {" + super.getTemplateAsString(elements) + "} while (true)"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "do / while"

  override def getSurroundSelectionRange(withDoWhileNode: ASTNode): TextRange = {
    val element: PsiElement = withDoWhileNode.getPsi match {
      case x: ScParenthesisedExpr => x.innerElement match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }
    val doWhileStmt = element.asInstanceOf[ScDo]

    val conditionNode: ASTNode = doWhileStmt.getNode.getLastChildNode.getTreePrev

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

    new TextRange(startOffset, endOffset)
  }
}
