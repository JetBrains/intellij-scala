package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc

import com.intellij.modcommand.ActionContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaDocSimpleData
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.surroundWith.ScalaModCommandSurrounder

trait ScalaDocWithSyntaxSurrounder extends ScalaModCommandSurrounder {
  override def isApplicable(elements: Array[PsiElement]): Boolean = elements != null && elements.nonEmpty

  override def surroundElements(elements: Array[PsiElement], context: ActionContext): Option[TextRange] = {
    val selection = context.selection()
    val startOffset = selection.getStartOffset
    val endOffset = selection.getEndOffset

    val element = elements(0)
    val offset = element.getTextOffset

    def getNewExprText(expr: String): String = expr.substring(0, startOffset - offset) + getSyntaxTag +
      expr.substring(startOffset - offset, endOffset - offset) + getSyntaxTag + expr.substring(endOffset - offset)

    val surroundedText = new StringBuilder()
    elements.foreach(surroundedText append _.getText)

    var newExpr = createScalaDocSimpleData(getNewExprText(surroundedText.toString()))(element.getManager)

    while (newExpr != null && newExpr.getNode.getElementType != ScalaDocTokenType.DOC_COMMENT_END) {
      element.getParent.addBefore(newExpr, element)
      newExpr = newExpr.getNextSibling
    }

    elements.foreach(_.delete())

    val range = new TextRange(endOffset + 2 * getSyntaxTag.length, endOffset + 2 * getSyntaxTag.length)
    Some(range)
  }

  def getSyntaxTag: String
}
