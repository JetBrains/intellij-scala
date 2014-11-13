package org.jetbrains.plugins.scala
package codeInspection.format

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Pavel Fatin
 */

class LegacyStringFormattingInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    case element if FormattedStringParser.extractFormatCall(element).isDefined =>
      holder.registerProblem(element, "Legacy string formatting, an interpolated string can be used instead", new QuickFix(element))
  }

  private class QuickFix(e: PsiElement) extends AbstractFixOnPsiElement("Convert to interpolated string", e) {
    def doApplyFix(project: Project) {
      val elem = getElement
      FormattedStringParser.parse(elem).foreach { parts =>

        val expression = {
          val s = InterpolatedStringFormatter.format(parts)
          ScalaPsiElementFactory.createExpressionFromText(s, elem.getManager)
        }

        elem.replace(expression)
      }
    }
  }
}
