package org.jetbrains.plugins.scala.codeInspection.semicolon

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.{PsiWhiteSpace, PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder, LocalInspectionTool}


/**
 * User: Alefas
 * Date: 09.02.12
 */

class ScalaUnnecessarySemicolonInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = false

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {

    new ScalaElementVisitor {
      def startOffset(element: PsiElement) = element.getTextRange.getStartOffset
      def endOffset(element: PsiElement) = element.getTextRange.getEndOffset
      def shiftInNewFile(offset: Int, semicolonOffset: Int): Int = offset + (if (offset > semicolonOffset) 1 else 0)

      override def visitElement(element: PsiElement) {
        if (element.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
          val file = element.getContainingFile
          val nextLeaf = file.findElementAt(endOffset(element))
          if (nextLeaf.isInstanceOf[PsiWhiteSpace] && nextLeaf.getText.contains("\n")) {
            val whitespaceOffset = endOffset(nextLeaf)
            val offset = startOffset(element)
            val text = file.getText
            val textWithoutSemicolon = text.substring(0, offset) + text.substring(offset + 1)
            val newFile = ScalaPsiElementFactory.createScalaFile(textWithoutSemicolon, element.getManager)
            var elem1 = file.findElementAt(offset - 1)
            var elem2 = newFile.findElementAt(offset - 1)
            while (elem1 != null && endOffset(elem1) <= offset && elem2 != null) {
              if (elem1.getText != elem2.getText) return
              if (elem1.getNode.getElementType != elem2.getNode.getElementType) return
              elem1 = elem1.getParent
              elem2 = elem2.getParent
            }
            if (elem2 == null) return
            if (shiftInNewFile(startOffset(elem2), offset) > startOffset(elem1) ||
                    shiftInNewFile(endOffset(elem2), offset) < endOffset(elem1)) return
            elem1 = file.findElementAt(whitespaceOffset)
            elem2 = newFile.findElementAt(whitespaceOffset - 1)
            while (elem1 != null && startOffset(elem1) >= whitespaceOffset && elem2 != null) {
              if (elem1.getText != elem2.getText) return
              if (elem1.getNode.getElementType != elem2.getNode.getElementType) return
              elem1 = elem1.getParent
              elem2 = elem2.getParent
            }
            if (elem2 == null) return
            if (shiftInNewFile(startOffset(elem2), offset) > startOffset(elem1) ||
                    shiftInNewFile(endOffset(elem2), offset) < endOffset(elem1)) return
            holder.registerProblem(holder.getManager.createProblemDescriptor(element, "Unnecessary semicolon", true,
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, new RemoveSemicolonFix(element)))
          }
        }
        super.visitElement(element)
      }
    }
  }
}

class RemoveSemicolonFix(element: PsiElement)
  extends AbstractFix("Remove unnecessary semicolon", element) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!element.isValid) return
    element.delete()
  }
}
