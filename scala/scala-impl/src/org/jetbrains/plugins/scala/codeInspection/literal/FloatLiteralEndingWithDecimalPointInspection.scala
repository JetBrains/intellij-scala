package org.jetbrains.plugins.scala
package codeInspection
package literal

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class FloatLiteralEndingWithDecimalPointInspection extends AbstractInspection() {
  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case lit: ScLiteral if lit.getText.endsWith(".") =>
      holder.registerProblem(lit, getDisplayName, new MakeDoubleFix(lit), new MakeFloatFix(lit), new AddZeroAfterDecimalPoint(lit))
  }
}

class MakeDoubleFix(lit: ScLiteral) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.floating.point", lit.getText.dropRight(1) + "d"), lit) {

  override protected def doApplyFix(l: ScLiteral)
                                   (implicit project: Project): Unit = {
    l.replace(createExpressionFromText(l.getText.dropRight(1) + "d"))
  }
}

class MakeFloatFix(lit: ScLiteral) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.floating.point", lit.getText.dropRight(1) + "f"), lit) {

  override protected def doApplyFix(l: ScLiteral)
                                   (implicit project: Project): Unit = {
    l.replace(createExpressionFromText(l.getText.dropRight(1) + "f"))
  }
}

class AddZeroAfterDecimalPoint(lit: ScLiteral) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.floating.point", lit.getText + "0"), lit) {

  override protected def doApplyFix(l: ScLiteral)
                                   (implicit project: Project): Unit = {
    l.replace(createExpressionFromText(l.getText + "0"))
  }
}
