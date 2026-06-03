package org.jetbrains.plugins.scala.codeInspection.format

import com.intellij.codeInspection._
import com.intellij.modcommand.{ActionContext, ModPsiUpdater, PsiUpdateModCommandAction}
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.format.LegacyStringFormattingInspection._
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class LegacyStringFormattingInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case element@ConcatenationOrFormattingTopmostStringParts(parts) if parts.sizeIs > 1 =>
      val fix = LocalQuickFix.from(new FormattingQuickFix(element))
      holder.registerProblem(element, ScalaInspectionBundle.message("legacy.string.formatting.use.interpolated.string"), fix)
    case _ =>
  }
}

object LegacyStringFormattingInspection {
  private object ConcatenationOrFormattingTopmostStringParts extends TopmostStringParts(ConcatenationOrFormattingStringParser)

  private object ConcatenationOrFormattingStringParser extends StringParser {
    override def parse(element: PsiElement): Option[Seq[StringPart]] =
      StringConcatenationParser.parse(element).orElse(FormattedStringParser.parse(element))
  }

  private class FormattingQuickFix(element: PsiElement) extends PsiUpdateModCommandAction[PsiElement](element) {
    override def getFamilyName: String = ScalaInspectionBundle.message("convert.to.interpolated.string")

    override protected def invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater): Unit =
      AnyStringParser.parse(element).foreach { parts =>
        val expression = createExpressionFromText(InterpolatedStringFormatter(ScInterpolatedStringLiteral.Kind.Standard).format(parts), element)(context.project())
        element.replace(expression)
      }
  }
}
