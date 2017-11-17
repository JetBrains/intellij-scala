package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement
import org.jetbrains.plugins.scala.project.ProjectContext

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


class ScalaDocHeaderBalanceQuickFix(opening: PsiElement, closing: PsiElement)
        extends AbstractFixOnTwoPsiElements(ScalaBundle.message("balance.header"), opening, closing) {

  override def getFamilyName: String = InspectionsUtil.SCALADOC

  override protected def doApplyFix(op: PsiElement, cl: PsiElement)
                                   (implicit project: Project): Unit = {
    if (op.getNode.getElementType != ScalaDocTokenType.VALID_DOC_HEADER ||
            cl.getNode.getElementType != ScalaDocTokenType.DOC_HEADER &&
                    cl.getNode.getElementType != ScalaDocTokenType.DOC_HEADER) {
      return
    }

    cl.replace(createDocHeaderElement(op.getText.length()))
  }
}

class ScalaDocMoveTextToNewLineQuickFix(textData: PsiElement)
        extends AbstractFixOnPsiElement(ScalaBundle.message("move.text.after.header.to.new.line"), textData) {
  override def getFamilyName: String = InspectionsUtil.SCALADOC

  def doApplyFix(project: Project) {
    implicit val ctx: ProjectContext = project

    val data = getElement
    if (!data.isValid) return

    data.getParent.addBefore(createDocWhiteSpace, data)
    data.getParent.addBefore(createLeadingAsterisk, data)
  }
}