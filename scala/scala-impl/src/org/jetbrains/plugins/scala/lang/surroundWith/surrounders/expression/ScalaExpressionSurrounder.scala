package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

/**
  * User: Dmitry.Krasilschikov
  * Date: 09.01.2007
  *
  */

import com.intellij.lang.ASTNode
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/*
 * Surrounds an expression and return an expression
 */
abstract class ScalaExpressionSurrounder extends Surrounder {
  def isApplicable(element: PsiElement): Boolean = {
    element match {
      case _: ScExpression | _: PsiWhiteSpace | _: ScValue | _: ScVariable | _: ScFunction | _: ScTypeAlias => true
      case e =>
        if (ScalaPsiUtil.isLineTerminator(e)) true
        else if (e.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) true
        else if (ScalaTokenTypes.COMMENTS_TOKEN_SET contains e.getNode.getElementType) true
        else false
    }
  }

  def needParenthesis(elements: Array[PsiElement]): Boolean = {
    if (elements.length > 1) return false
    val element = elements(0)
    val parent = element.getParent
    parent match {
      case _: ScInfixExpr => true
      case _: ScReferenceExpression => true
      case _: ScPrefixExpr => true
      case _: ScPostfixExpr => true
      case _ => false
    }
  }

  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    for (element <- elements)
      if (!isApplicable(element)) return false
    true
  }

  override def surroundElements(project: Project, editor: Editor, elements: Array[PsiElement]): TextRange = {
    surroundWithReformat(project, editor, elements, doReformat = false)
  }

  def surroundWithReformat(project: Project, editor: Editor, elements: Array[PsiElement], doReformat: Boolean): TextRange = {
    val newNode = surroundPsi(elements).getNode
    var childNode: ASTNode = null

    for (child <- elements) {
      if (childNode == null) {
        childNode = child.getNode
        childNode.getTreeParent.replaceChild(childNode, newNode)
      }
      else {
        childNode = child.getNode
        childNode.getTreeParent.removeChild(childNode)
      }
    }
    if (doReformat) {
      CodeStyleManager.getInstance(project).reformat(newNode.getPsi)
    }
    getSurroundSelectionRange(newNode)
  }

  def surroundPsi(elements: Array[PsiElement]): ScExpression =
    createExpressionFromText(getTemplateAsString(elements).parenthesize(needParenthesis(elements)))(elements(0).getManager)

  def getTemplateAsString(elements: Array[PsiElement]): String = {
    var s: String = ""
    for (element <- elements) {
      s = s + element.getNode.getText
    }
    s
  }

  def getSurroundSelectionRange(node: ASTNode): TextRange
}