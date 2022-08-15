package org.jetbrains.plugins.scala
package codeInspection.format

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.format.LegacyStringFormattingInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText


class LegacyStringFormattingInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case element@ConcatenationOrFormattingTopmostStringParts(parts) if parts.sizeIs > 1 =>
      holder.registerProblem(element, ScalaInspectionBundle.message("legacy.string.formatting.use.interpolated.string"), new FormattingQuickFix(element))
    case _ =>
  }
}

object LegacyStringFormattingInspection {
  object ConcatenationOrFormattingTopmostStringParts extends TopmostStringParts(ConcatenationOrFormattingStringParser)

  object ConcatenationOrFormattingStringParser extends StringParser {
    override def parse(element: PsiElement): Option[Seq[StringPart]] =
      StringConcatenationParser.parse(element).orElse(FormattedStringParser.parse(element))
  }

  private class FormattingQuickFix(e: PsiElement) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.interpolated.string"), e) {
    override protected def doApplyFix(elem: PsiElement)(implicit project: Project): Unit = {
      AnyStringParser.parse(elem).foreach { parts =>
        val expression = createExpressionFromText(InterpolatedStringFormatter.format(parts))
        elem.replace(expression)
      }
    }
  }
}