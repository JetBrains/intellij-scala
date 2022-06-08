package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFor, ScFunctionExpr, ScMethodCall, ScReferenceExpression, ScReturn}
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
      case scReturn: ScReturn if isNonLocal(scReturn) &&
        isInspectionAllowed(scReturn, checkCompilerOption, "-Xlint:nonlocal-return") =>
        Some(
            manager.createProblemDescriptor(
              scReturn,
              annotationDescription,
              isOnTheFly,
              Array.empty[LocalQuickFix],
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        )
      case _ =>
        None
    }
}

object NonLocalReturnInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("nonlocal.return.statement")

  private def isSynchronized(methodCall: ScMethodCall): Boolean = {
    val ref = methodCall.findFirstChildByTypeScala[ScReferenceExpression](ScalaElementType.REFERENCE_EXPRESSION)
    ref.exists(_.refName.contentEquals("synchronized"))
  }

  @tailrec
  private def isNonLocal(elem: ScalaPsiElement): Boolean =
    elem.getParent match {
      case _: ScFunctionDefinition => false
      case _: ScFunctionExpr       => true
      case m: ScMethodCall         => !isSynchronized(m)
      case _: ScFor                => true
      case parent: ScalaPsiElement => isNonLocal(parent)
      case _                       => false
    }
}
