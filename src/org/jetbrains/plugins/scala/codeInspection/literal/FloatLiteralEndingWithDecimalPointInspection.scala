package org.jetbrains.plugins.scala
package codeInspection
package literal

import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class FloatLiteralEndingWithDecimalPointInspection extends AbstractInspection("FloatLiteralEndingWithDecimalPoint", "Floating point literal ending with '.'"){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case lit: ScLiteral if lit.getText.endsWith(".") =>
      holder.registerProblem(lit, getDisplayName, new MakeDoubleFix(lit), new MakeFloatFix(lit), new AddZeroAfterDecimalPoint(lit))
  }
}

class MakeDoubleFix(lit: ScLiteral) extends AbstractFix("Convert to %s".format(lit.getText.dropRight(1) + "d"), lit) {
  def doApplyFix(project: Project) {
    val exp = ScalaPsiElementFactory.createExpressionFromText(lit.getText.dropRight(1) + "d", lit.getManager)
    lit.replace(exp)
  }
}

class MakeFloatFix(lit: ScLiteral) extends AbstractFix("Convert to %s".format(lit.getText.dropRight(1) + "f"), lit) {
  def doApplyFix(project: Project) {
    val exp = ScalaPsiElementFactory.createExpressionFromText(lit.getText.dropRight(1) + "f", lit.getManager)
    lit.replace(exp)
  }
}

class AddZeroAfterDecimalPoint(lit: ScLiteral) extends AbstractFix("Convert to %s".format(lit.getText + "0"), lit) {
  def doApplyFix(project: Project) {
    val exp = ScalaPsiElementFactory.createExpressionFromText(lit.getText + "0", lit.getManager)
    lit.replace(exp)
  }
}
