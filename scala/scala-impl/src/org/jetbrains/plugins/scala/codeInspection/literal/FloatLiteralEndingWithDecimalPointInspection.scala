package org.jetbrains.plugins.scala.codeInspection.literal

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

final class FloatLiteralEndingWithDecimalPointInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case lit: ScLiteral if lit.getText.endsWith(".") =>
      holder.registerProblem(lit, getDisplayName, new MakeDoubleFix(lit), new MakeFloatFix(lit), new AddZeroAfterDecimalPoint(lit))
    case _ =>
  }
}

final class MakeDoubleFix(lit: ScLiteral)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.floating.point", lit.getText.dropRight(1) + "d"), lit)
    with DumbAware {
  override protected def doApplyFix(l: ScLiteral)
                                   (implicit project: Project): Unit =
    l.replace(createExpressionFromText(l.getText.dropRight(1) + "d", l))
}

final class MakeFloatFix(lit: ScLiteral)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.floating.point", lit.getText.dropRight(1) + "f"), lit)
    with DumbAware {
  override protected def doApplyFix(l: ScLiteral)
                                   (implicit project: Project): Unit =
    l.replace(createExpressionFromText(l.getText.dropRight(1) + "f", l))
}

final class AddZeroAfterDecimalPoint(lit: ScLiteral)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.floating.point", lit.getText + "0"), lit)
    with DumbAware {
  override protected def doApplyFix(l: ScLiteral)
                                   (implicit project: Project): Unit =
    l.replace(createExpressionFromText(l.getText + "0", l))
}
