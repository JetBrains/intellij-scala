package org.jetbrains.plugins.scala
package codeInspection.format

import format._
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import codeInspection.{AbstractFix, AbstractInspection}

/**
 * Pavel Fatin
 */

class LegacyStringFormattingInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    case element if FormattedStringParser.extractFormatCall(element).isDefined =>
      holder.registerProblem(element, "Legacy string formatting, an interpolated string can be used instead", new QuickFix(element))
  }

  private class QuickFix(e: PsiElement) extends AbstractFix("Convert to interpolated string", e) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
      FormattedStringParser.parse(e).foreach { parts =>

        val expression = {
          val s = InterpolatedStringFormatter.format(parts)
          ScalaPsiElementFactory.createExpressionFromText(s, e.getManager)
        }

        e.replace(expression)
      }
    }
  }
}
