package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText

class ScalaUnnecessarySemicolonInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = false

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {

    new ScalaElementVisitor {
      def startOffset(element: PsiElement): Int = element.getTextRange.getStartOffset
      def endOffset(element: PsiElement): Int = element.getTextRange.getEndOffset
      def shiftInNewFile(offset: Int, semicolonOffset: Int): Int = offset + (if (offset > semicolonOffset) 1 else 0)

      override def visitElement(element: PsiElement): Unit = {
        if (element.elementType == ScalaTokenTypes.tSEMICOLON) {
          val file = element.getContainingFile
          val canRemove = element.nextLeaf match {
            case None => true
            case Some(nextLeaf) if nextLeaf.getText.contains("\n") =>
              val whitespaceOffset = endOffset(nextLeaf)
              val offset = startOffset(element)
              val textWithoutSemicolon = removeChar(file.charSequence, offset)
              val newFile = createScalaFileFromText(textWithoutSemicolon)(element.getManager)
              var elem1 = file.findElementAt(offset - 1)
              var elem2 = newFile.findElementAt(offset - 1)
              while (elem1 != null && endOffset(elem1) <= offset && elem2 != null) {
                if (!elem1.textMatches(elem2.getText)) return
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
                if (!elem1.textMatches(elem2.getText)) return
                if (elem1.getNode.getElementType != elem2.getNode.getElementType) return
                elem1 = elem1.getParent
                elem2 = elem2.getParent
              }
              if (elem2 == null) return
              if (shiftInNewFile(startOffset(elem2), offset) > startOffset(elem1) ||
                      shiftInNewFile(endOffset(elem2), offset) < endOffset(elem1)) return
              true
            case _ =>
              false
          }

          if (canRemove) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(element, ScalaInspectionBundle.message("unnecessary.semicolon"), true,
              ProblemHighlightType.LIKE_UNUSED_SYMBOL, isOnTheFly, new RemoveSemicolonFix(element)))
          }
        }
        super.visitElement(element)
      }
    }
  }

  private def removeChar(cs: CharSequence, offset: Int): String = {
    val builder = new java.lang.StringBuilder(cs.length() - 1)
    builder.append(cs.subSequence(0, offset))
    builder.append(cs.subSequence(offset + 1, cs.length()))
    builder.toString
  }
}

class RemoveSemicolonFix(element: PsiElement) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.unnecessary.semicolon"), element) {

  override protected def doApplyFix(elem: PsiElement)
                                   (implicit project: Project): Unit = {
    elem.delete()
  }
}
