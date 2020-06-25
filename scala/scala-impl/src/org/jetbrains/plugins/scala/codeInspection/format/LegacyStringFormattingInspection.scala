package org.jetbrains.plugins.scala
package codeInspection.format

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * Pavel Fatin
 */

class LegacyStringFormattingInspection extends AbstractInspection {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case element if FormattedStringParser.extractFormatCall(element).isDefined =>
      holder.registerProblem(element, ScalaInspectionBundle.message("legacy.string.formatting.use.interpolated.string"), new QuickFix(element))
  }

  private class QuickFix(e: PsiElement) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.interpolated.string"), e) {

    override protected def doApplyFix(elem: PsiElement)
                                     (implicit project: Project): Unit = {
      FormattedStringParser.parse(elem).foreach { parts =>
        val expression = createExpressionFromText(InterpolatedStringFormatter.format(parts))
        elem.replace(expression)
      }
    }
  }
}
