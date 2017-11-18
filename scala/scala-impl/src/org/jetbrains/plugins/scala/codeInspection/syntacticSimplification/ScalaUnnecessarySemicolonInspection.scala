package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiWhiteSpace}
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText

/**
 * User: Alefas
 * Date: 09.02.12
 */

class ScalaUnnecessarySemicolonInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = false

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {

    new ScalaElementVisitor {
      def startOffset(element: PsiElement): Int = element.getTextRange.getStartOffset
      def endOffset(element: PsiElement): Int = element.getTextRange.getEndOffset
      def shiftInNewFile(offset: Int, semicolonOffset: Int): Int = offset + (if (offset > semicolonOffset) 1 else 0)

      override def visitElement(element: PsiElement) {
        if (element.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
          val file = element.getContainingFile
          val nextLeaf = file.findElementAt(endOffset(element))
          if (nextLeaf.isInstanceOf[PsiWhiteSpace] && nextLeaf.getText.contains("\n")) {
            val whitespaceOffset = endOffset(nextLeaf)
            val offset = startOffset(element)
            val text = file.getText
            val textWithoutSemicolon = text.take(offset) + text.drop(offset + 1)
            val newFile = createScalaFileFromText(textWithoutSemicolon)(element.getManager)
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

class RemoveSemicolonFix(element: PsiElement) extends AbstractFixOnPsiElement("Remove unnecessary semicolon", element) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    elem.delete()
  }
}
