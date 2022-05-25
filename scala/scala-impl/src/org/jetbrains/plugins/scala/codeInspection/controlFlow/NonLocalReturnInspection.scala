package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScMethodCall, ScReturn}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import javax.swing.JComponent
import scala.annotation.tailrec
import scala.beans.BooleanBeanProperty

final class NonLocalReturnInspection extends AbstractRegisteredInspection {
  import NonLocalReturnInspection._
  import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._

  @BooleanBeanProperty
  var checkCompilerOption: Boolean = true

  @Override
  override def createOptionsPanel(): JComponent =
    InspectionOptionsPanel.singleCheckBox(
      this,
      ScalaInspectionBundle.message("nonlocal.return.check.compiler.option"),
      "checkCompilerOption"
    )

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case function: ScFunctionDefinition if isInspectionAllowed(function, checkCompilerOption, "-Xlint:nonlocal-return") =>
        function.returnUsages.collectFirst {
            case scReturn: ScReturn if isInsideAnonymousFunction(scReturn) =>
              manager.createProblemDescriptor(
                scReturn,
                annotationDescription,
                isOnTheFly,
                Array.empty[LocalQuickFix],
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
              )
          }
      case _ =>
        None
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
}
