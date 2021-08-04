package org.jetbrains.plugins.scala
package codeInspection.format

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.format.LegacyStringFormattingInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText


class LegacyStringFormattingInspection extends AbstractRegisteredInspection {

  protected override def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           @Nls descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case ConcatenationOrFormattingTopmostStringParts(parts) if parts.sizeIs > 1 =>
        val quickfix = new FormattingQuickFix(element)
        Some(manager.createProblemDescriptor(element, ScalaInspectionBundle.message("legacy.string.formatting.use.interpolated.string"), quickfix, highlightType, isOnTheFly))
      case _ =>
        None
    }
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