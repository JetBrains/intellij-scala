package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement

final class ScalaDocUnbalancedHeaderInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    new ScalaElementVisitor {
      override def visitWikiSyntax(syntaxElement: ScDocSyntaxElement): Unit = {

        val firstChild = syntaxElement.getFirstChild
        val lastChild = syntaxElement.getLastChild

        if (!ScalaDocUnbalancedHeaderInspection.isApplicable(firstChild, lastChild))
          return

        val isUnbalanced = syntaxElement.getFirstChild.getTextLength != lastChild.getTextLength
        if (isUnbalanced) {
          val description = getDisplayName
          val problem = holder.getManager.createProblemDescriptor(
            lastChild, description, true,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            isOnTheFly,
            new ScalaDocHeaderBalanceQuickFix(syntaxElement.getFirstChild, lastChild)
          )
          holder.registerProblem(problem)
        }

        val siblingElementsOnSameLine = syntaxElement.nextSiblings.takeWhile(!isDocLineBreak(_)).toSeq
        if (siblingElementsOnSameLine.nonEmpty) {
          val isJustAWhitespace =
            siblingElementsOnSameLine.sizeIs == 1 &&
              siblingElementsOnSameLine.head.elementType == ScalaDocTokenType.DOC_WHITESPACE

          if (!isJustAWhitespace) {
            val description = ScalaInspectionBundle.message("all.text.from.header.closing.tag.to.end.of.line.will.be.lost")
            val first       = siblingElementsOnSameLine.head
            val last        = siblingElementsOnSameLine.last
            val problem     = holder.getManager.createProblemDescriptor(
              first, last, description,
              ProblemHighlightType.WEAK_WARNING, isOnTheFly,
              new ScalaDocMoveTextToNewLineQuickFix(first, last)
            )
            holder.registerProblem(problem)
          }
        }
      }
    }

  private def isDocLineBreak(el: PsiElement): Boolean =
    el.elementType == ScalaDocTokenType.DOC_WHITESPACE && el.textContains('\n')
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

final class ScalaDocHeaderBalanceQuickFix(opening: PsiElement, closing: PsiElement)
  extends AbstractFixOnTwoPsiElements(ScalaBundle.message("balance.header"), opening, closing)
    with DumbAware {
  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(openTag: PsiElement, closeTag: PsiElement)
                                   (implicit project: Project): Unit = {
    if (ScalaDocUnbalancedHeaderInspection.isApplicable(openTag, closeTag)) {
      val newCloseTag = createScalaDocHeaderElement(openTag.getText.length())
      closeTag.replace(newCloseTag)
    }
  }
}

final class ScalaDocMoveTextToNewLineQuickFix(startElement: PsiElement, endElement: PsiElement)
  extends AbstractFixOnTwoPsiElements(ScalaBundle.message("move.text.after.header.to.new.line"), startElement, endElement)
    with DumbAware {
  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(first: PsiElement, second: PsiElement)(implicit project: Project): Unit = {
    val parent = first.getParent
    parent.addBefore(createScalaDocWhiteSpaceWithNewLine, first)
    val addedAst = parent.addBefore(createScalaDocLeadingAsterisk, first)

    CodeStyleManager.getInstance(project)
      .reformatText(parent.getContainingFile, addedAst.startOffset, first.endOffset)
  }
}
