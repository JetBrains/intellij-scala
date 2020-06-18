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

class ScalaDocUnbalancedHeaderInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitWikiSyntax(s: ScDocSyntaxElement): Unit = {
        import ScalaDocTokenType._

        val firstChild = s.getFirstChild
        val lastChild = s.getLastChild

        if (!ScalaDocUnbalancedHeaderInspection.isApplicable(firstChild, lastChild))
          return

        if (s.getFirstChild.getTextLength != lastChild.getTextLength) {
          holder.registerProblem(holder.getManager.createProblemDescriptor(
            lastChild, getDisplayName, true,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            isOnTheFly,
            new ScalaDocHeaderBalanceQuickFix(s.getFirstChild, lastChild)
          ))
        }

        var sibl = s.getNextSibling
        val firstSibl = sibl
        while (sibl != null && sibl.getNode.getElementType != DOC_COMMENT_END &&
          sibl.getNode.getElementType != DOC_WHITESPACE) {
          val highlightedElement = if (s.getNextSibling != null) s.getNextSibling else s
          holder.registerProblem(holder.getManager.createProblemDescriptor(
            highlightedElement, highlightedElement,
            ScalaInspectionBundle.message("all.text.from.header.closing.tag.to.end.of.line.will.be.lost"),
            ProblemHighlightType.WEAK_WARNING, isOnTheFly,
            new ScalaDocMoveTextToNewLineQuickFix(firstSibl)
          ))
          sibl = sibl.getNextSibling
        }
      }
    }
  }
}

object ScalaDocUnbalancedHeaderInspection {
  def isApplicable(openTag: PsiElement, closeTag: PsiElement): Boolean = {
    import ScalaDocTokenType._
    (openTag.getNode.getElementType, closeTag.getNode.getElementType) match {
      case (VALID_DOC_HEADER, VALID_DOC_HEADER | DOC_HEADER) => true
      case _ => false
    }
  }
}


class ScalaDocHeaderBalanceQuickFix(opening: PsiElement, closing: PsiElement)
  extends AbstractFixOnTwoPsiElements(ScalaBundle.message("balance.header"), opening, closing) {

  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(openTag: PsiElement, closeTag: PsiElement)
                                   (implicit project: Project): Unit = {
    if (ScalaDocUnbalancedHeaderInspection.isApplicable(openTag, closeTag)) {
      val newCloseTag = createDocHeaderElement(openTag.getText.length())
      closeTag.replace(newCloseTag)
    }
  }
}

class ScalaDocMoveTextToNewLineQuickFix(textData: PsiElement)
  extends AbstractFixOnPsiElement(ScalaBundle.message("move.text.after.header.to.new.line"), textData) {

  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(data: PsiElement)
                                   (implicit project: Project): Unit = {
    val parent = data.getParent
    parent.addBefore(createDocWhiteSpaceWithNewLine, data)
    parent.addBefore(createLeadingAsterisk, data)
  }
}