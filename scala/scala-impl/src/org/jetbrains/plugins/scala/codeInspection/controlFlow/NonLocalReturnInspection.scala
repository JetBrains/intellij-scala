package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.{checkbox, pane}
import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder, UpdateInspectionOptionFix}
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import scala.annotation.tailrec
import scala.beans.BooleanBeanProperty

final class NonLocalReturnInspection extends LocalInspectionTool {

  import NonLocalReturnInspection._
  import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._

  @BooleanBeanProperty
  var checkCompilerOption: Boolean = true

  override def getOptionsPane: OptPane = pane(checkbox(propertyName, checkboxLabel))

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case scReturn: ScReturn if isNonLocal(scReturn) &&
      isInspectionAllowed(scReturn, checkCompilerOption, "-Xlint:nonlocal-return") =>
      if (!checkCompilerOption) {
        val fix = new UpdateInspectionOptionFix(this, propertyName, ScalaInspectionBundle.message("fix.nonlocal.return.check.compiler.option"), true)
        val quickFix = LocalQuickFix.from(fix)
        holder.registerProblem(scReturn, annotationDescription, quickFix)
      } else {
        holder.registerProblem(scReturn, annotationDescription)
      }
    case _ =>
  }
}

object NonLocalReturnInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("nonlocal.return.statement")

  @NonNls
  private val propertyName: String = "checkCompilerOption"

  @Nls
  private val checkboxLabel: String = ScalaInspectionBundle.message("nonlocal.return.check.compiler.option")

  private def isSynchronized(methodCall: ScMethodCall): Boolean = {
    val ref = methodCall.findFirstChildByTypeScala[ScReferenceExpression](ScalaElementType.REFERENCE_EXPRESSION)
    ref.exists(_.refName.contentEquals("synchronized"))
  }

  @tailrec
  private def isNonLocal(elem: ScalaPsiElement): Boolean = elem.getParent match {
    case _: ScFunctionDefinition => false
    case _: ScFunctionExpr       => true
    case m: ScMethodCall         => !isSynchronized(m)
    case _: ScFor                => true
    case parent: ScalaPsiElement => isNonLocal(parent)
    case _                       => false
  }
}
