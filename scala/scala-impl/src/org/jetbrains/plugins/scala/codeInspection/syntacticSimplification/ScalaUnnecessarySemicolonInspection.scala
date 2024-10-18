package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText

final class ScalaUnnecessarySemicolonInspection extends LocalInspectionTool with DumbAware {
  override def isEnabledByDefault: Boolean = false

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    new ScalaElementVisitor {
      def shiftInNewFile(offset: Int, semicolonOffset: Int): Int = offset + (if (offset > semicolonOffset) 1 else 0)

      override def visitElement(element: PsiElement): Unit = {
        if (element.elementType == ScalaTokenTypes.tSEMICOLON) {
          val file = element.getContainingFile
          val canRemove = element.nextLeaf match {
            case None => true
            case Some(nextLeaf) if nextLeaf.getText.contains("\n") =>
              val whitespaceOffset = nextLeaf.endOffset
              val offset = element.startOffset
              val textWithoutSemicolon = removeChar(file.charSequence, offset)
              val newFile = createScalaFileFromText(textWithoutSemicolon, element)(element.getManager)
              var elem1 = file.findElementAt(offset - 1)
              var elem2 = newFile.findElementAt(offset - 1)
              while (elem1 != null && elem1.endOffset <= offset && elem2 != null) {
                if (!elem1.textMatches(elem2.getText)) return
                if (elem1.getNode.getElementType != elem2.getNode.getElementType) return
                elem1 = elem1.getParent
                elem2 = elem2.getParent
              }
              if (elem2 == null) return
              if (shiftInNewFile(elem2.startOffset, offset) > elem1.startOffset ||
                shiftInNewFile(elem2.endOffset, offset) < elem1.endOffset) return
              elem1 = file.findElementAt(whitespaceOffset)
              elem2 = newFile.findElementAt(whitespaceOffset - 1)
              while (elem1 != null && elem1.startOffset >= whitespaceOffset && elem2 != null) {
                if (!elem1.textMatches(elem2.getText)) return
                if (elem1.getNode.getElementType != elem2.getNode.getElementType) return
                elem1 = elem1.getParent
                elem2 = elem2.getParent
              }
              if (elem2 == null) return
              if (shiftInNewFile(elem2.startOffset, offset) > elem1.startOffset ||
                shiftInNewFile(elem2.endOffset, offset) < elem1.endOffset) return
              true
            case _ =>
              false
          }

          if (canRemove) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(element, ScalaInspectionBundle.message("unnecessary.semicolon"), true,
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, new RemoveSemicolonFix(element)))
          }
        }
        super.visitElement(element)
      }
    }

  private def removeChar(cs: CharSequence, offset: Int): String = {
    val builder = new java.lang.StringBuilder(cs.length() - 1)
    builder.append(cs.subSequence(0, offset))
    builder.append(cs.subSequence(offset + 1, cs.length()))
    builder.toString
  }
}

final class RemoveSemicolonFix(element: PsiElement)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.unnecessary.semicolon"), element)
    with DumbAware {
  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit =
    elem.delete()
}
