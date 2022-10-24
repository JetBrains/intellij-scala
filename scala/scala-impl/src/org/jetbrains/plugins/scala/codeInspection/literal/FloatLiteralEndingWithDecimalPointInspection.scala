package org.jetbrains.plugins.scala.codeInspection.literal

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.annotation.unused

@unused("registered in scala-plugin-common.xml")
class FloatLiteralEndingWithDecimalPointInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case lit: ScLiteral if lit.getText.endsWith(".") =>
      holder.registerProblem(lit, getDisplayName, new MakeDoubleFix(lit), new MakeFloatFix(lit), new AddZeroAfterDecimalPoint(lit))
    case _ =>
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
