package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle, createSetInspectionOptionFix}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import javax.swing.JComponent
import scala.annotation.tailrec
import scala.beans.BooleanBeanProperty

final class NonLocalReturnInspection extends LocalInspectionTool {
  import NonLocalReturnInspection._
  import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._

  @BooleanBeanProperty
  var checkCompilerOption: Boolean = true

  @Override
  override def createOptionsPanel(): JComponent =
    InspectionOptionsPanel.singleCheckBox(
      this,
      ScalaInspectionBundle.message("nonlocal.return.check.compiler.option"),
      propertyName
    )

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case scReturn: ScReturn if isNonLocal(scReturn) &&
      isInspectionAllowed(scReturn, checkCompilerOption, "-Xlint:nonlocal-return") =>
      if (!checkCompilerOption) {
        val fix = createSetInspectionOptionFix(this, scReturn, propertyName, ScalaInspectionBundle.message("fix.nonlocal.return.check.compiler.option"))
        holder.registerProblem(scReturn, annotationDescription, fix)
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
