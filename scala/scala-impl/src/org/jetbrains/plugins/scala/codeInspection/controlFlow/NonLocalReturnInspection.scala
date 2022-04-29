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
import org.jetbrains.plugins.scala.project.ModuleExt

import javax.swing.JComponent
import scala.annotation.tailrec

final class NonLocalReturnInspection extends AbstractRegisteredInspection {
  import NonLocalReturnInspection._

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case function: ScFunctionDefinition if !checkCompilerOption || isCompilerOptionPresent(function) =>
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

  private def isCompilerOptionPresent(elem: ScalaPsiElement): Boolean =
    elem.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains("-Xlint:nonlocal-return"))

  var checkCompilerOption: Boolean = false

  def isCheckCompilerOption: Boolean = checkCompilerOption

  def setCheckCompilerOption(checkCompilerOption: Boolean): Unit = {
    this.checkCompilerOption = checkCompilerOption
  }

  @Override
  override def createOptionsPanel(): JComponent =
    new SingleCheckboxOptionsPanel(
      ScalaInspectionBundle.message("nonlocal.return.check.compiler.option"),
      this,
      "checkCompilerOption"
    )
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

  private def createQuickFixes(scReturn: ScReturn): Array[LocalQuickFix] =
    (new RemoveExpressionQuickFix(scReturn) ::
      (if (scReturn.expr.isDefined) List(new RemoveReturnKeywordQuickFix(scReturn)) else Nil)
    ).toArray[LocalQuickFix]
}
