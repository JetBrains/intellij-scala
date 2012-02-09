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
      override def visitElement(element: PsiElement) {
        if (element.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
          val file = element.getContainingFile
          val nextLeaf = file.findElementAt(element.getTextRange.getEndOffset)
          if (nextLeaf.isInstanceOf[PsiWhiteSpace] && nextLeaf.getText.contains("\n")) {
            val whitespaceOffset = nextLeaf.getTextRange.getEndOffset
            val offset = element.getTextRange.getStartOffset
            val text = file.getText
            val textWithoutSemicolon = text.substring(0, offset) + text.substring(offset + 1)
            val newFile = ScalaPsiElementFactory.createScalaFile(textWithoutSemicolon, element.getManager)
            var elem1 = file.findElementAt(offset - 1)
            var elem2 = newFile.findElementAt(offset - 1)
            while (elem1 != null && elem1.getTextRange.getEndOffset <= offset && elem2 != null) {
              if (elem1.getText != elem2.getText) return
              elem1 = elem1.getParent
              elem2 = elem2.getParent
            }
            if (elem2 == null) return
            elem1 = file.findElementAt(whitespaceOffset)
            elem2 = newFile.findElementAt(whitespaceOffset - 1)
            while (elem1 != null && elem1.getTextRange.getStartOffset >= whitespaceOffset && elem2 != null) {
              if (elem1.getText != elem2.getText) return
              elem1 = elem1.getParent
              elem2 = elem2.getParent
            }
            if (elem2 == null) return
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
