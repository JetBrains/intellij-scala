package org.jetbrains.plugins.scala
package lang.surroundWith.surrounders.scaladoc

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaDocSimpleData
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType

trait ScalaDocWithSyntaxSurrounder extends Surrounder {
  override def isApplicable(elements: Array[PsiElement]): Boolean = elements != null && elements.length >= 1

  override def surroundElements(project: Project, editor: Editor, elements: Array[PsiElement]): TextRange = {
    val startOffset = editor.getSelectionModel.getSelectionStart
    val endOffset = editor.getSelectionModel.getSelectionEnd

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

    new TextRange(endOffset + 2*getSyntaxTag.length, endOffset + 2*getSyntaxTag.length)
  }

  def getSyntaxTag: String
}
