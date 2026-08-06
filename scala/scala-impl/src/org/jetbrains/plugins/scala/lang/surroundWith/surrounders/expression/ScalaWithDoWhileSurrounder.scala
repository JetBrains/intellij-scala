package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ToNullSafe}
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

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription = "do / while"

  override def getSurroundSelectionRange(withDoWhileNode: ASTNode): Option[TextRange] =
    unwrapParenthesis(withDoWhileNode) match {
      case Some(stmt: ScDo) =>
        val conditionNode = stmt.getNode
          .nullSafe
          .map(_.getLastChildNode)
          .map(_.getTreePrev)
        conditionNode.map(_.getTextRange).toOption
      case _ => None
    }

  override protected val isApplicableToMultipleElements: Boolean = true
}
