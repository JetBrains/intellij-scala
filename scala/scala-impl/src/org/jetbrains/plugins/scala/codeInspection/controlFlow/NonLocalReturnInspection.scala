package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.quickfix.{RemoveExpressionQuickFix, RemoveReturnKeywordQuickFix}
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScMethodCall, ScReturn}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt}

import javax.swing.JComponent
import scala.annotation.tailrec
import scala.beans.BooleanBeanProperty

final class NonLocalReturnInspection extends AbstractRegisteredInspection {
  import NonLocalReturnInspection._

  @BooleanBeanProperty
  var checkCompilerOption: Boolean = false

  @Override
  override def createOptionsPanel(): JComponent =
    new SingleCheckboxOptionsPanel(
      ScalaInspectionBundle.message("nonlocal.return.check.compiler.option"),
      this,
      "checkCompilerOption"
    )

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    def isCompilerOptionPresent: Boolean =
      element.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains("-Xlint:nonlocal-return"))

    element match {
      case function: ScFunctionDefinition if !checkCompilerOption || isCompilerOptionPresent =>
        function.returnUsages.collectFirst {
            case scReturn: ScReturn if isInsideAnonymousFunction(scReturn) =>
              manager.createProblemDescriptor(
                scReturn,
                annotationDescription,
                isOnTheFly,
                createQuickFixes(scReturn),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
              )
          }
      case _ =>
        None
    }
  }
}

object NonLocalReturnInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("nonlocal.return.statement")

  @tailrec
  private def isInsideAnonymousFunction(elem: ScalaPsiElement): Boolean =
    elem.getParent match {
      case _: ScFunctionDefinition => false
      case _: ScFunctionExpr       => true
      case _: ScMethodCall         => true
      case parent: ScalaPsiElement => isInsideAnonymousFunction(parent)
    }

  private def createQuickFixes(scReturn: ScReturn): Array[LocalQuickFix] = {
    val fix1 = new RemoveExpressionQuickFix(scReturn)
    lazy val fix2 = new RemoveReturnKeywordQuickFix(scReturn)
    if (scReturn.expr.isDefined) Array(fix1, fix2) else Array(fix1)
  }
}
