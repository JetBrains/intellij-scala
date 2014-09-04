package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */

class ScalaDocUnbalancedHeaderInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType._
      override def visitWikiSyntax(s: ScDocSyntaxElement) {
        val firstChildElementType = s.getFirstChild.getNode.getElementType
        val lastChildElementType = s.getLastChild.getNode.getElementType
        
        if (firstChildElementType == null) {
          return
        }
        
        if (firstChildElementType == VALID_DOC_HEADER && (lastChildElementType == VALID_DOC_HEADER ||
                lastChildElementType == DOC_HEADER)) {
          if (s.getFirstChild.getTextLength != s.getLastChild.getTextLength) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(s.getLastChild, getDisplayName, true,
              ProblemHighlightType.GENERIC_ERROR, isOnTheFly, new ScalaDocHeaderBalanceQuickFix(s.getFirstChild, s.getLastChild)))
          }
          
          var sibl = s.getNextSibling
          val firstSibl = sibl
          while (sibl != null && sibl.getNode.getElementType != DOC_COMMENT_END &&
                  sibl.getNode.getElementType != DOC_WHITESPACE) {
            val highlightedElement = if (s.getNextSibling != null) s.getNextSibling else s
            holder.registerProblem(holder.getManager.createProblemDescriptor(highlightedElement, highlightedElement,
              "All text from header closing tag to end of line will be lost",
              ProblemHighlightType.WEAK_WARNING, isOnTheFly, new ScalaDocMoveTextToNewLineQuickFix(firstSibl)))
            sibl = sibl.getNextSibling
          }
        }
      }
    }
  }
}


class ScalaDocHeaderBalanceQuickFix(opening: PsiElement, closing: PsiElement) extends LocalQuickFix {
  def getName: String = "Balance Header"

  def getFamilyName: String = InspectionsUtil.SCALADOC

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!opening.isValid || !closing.isValid) return
    if (opening.getNode.getElementType != ScalaDocTokenType.VALID_DOC_HEADER ||
            closing.getNode.getElementType != ScalaDocTokenType.DOC_HEADER &&
                    closing.getNode.getElementType != ScalaDocTokenType.DOC_HEADER) {
      return
    }

    closing.replace(ScalaPsiElementFactory.createDocHeaderElement(opening.getText.length(), opening.getManager))
  }
}

class ScalaDocMoveTextToNewLineQuickFix(textData: PsiElement) extends LocalQuickFix {
  def getName: String = "Move text after header closing to new line"

  def getFamilyName: String = InspectionsUtil.SCALADOC

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!textData.isValid) return

    textData.getParent.addBefore(ScalaPsiElementFactory.createDocWhiteSpace(textData.getManager), textData)
    textData.getParent.addBefore(ScalaPsiElementFactory.createLeadingAsterisk(textData.getManager), textData)
  }
}