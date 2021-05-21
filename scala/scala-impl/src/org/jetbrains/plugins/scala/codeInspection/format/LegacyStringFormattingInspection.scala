package org.jetbrains.plugins.scala
package codeInspection.format

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.annotation.nowarn

// TODO: this inspection is disabled by default, not covered with tests and looks like broken
//  see the statistics, whether remove it if it's not used, or retest and cover with tests
/**
 * Pavel Fatin
 */
@nowarn("msg=" + AbstractInspection.DeprecationText)
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
