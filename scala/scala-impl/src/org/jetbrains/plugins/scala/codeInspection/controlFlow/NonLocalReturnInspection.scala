package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.quickfix.{RemoveExpressionQuickFix, RemoveReturnKeywordQuickFix}
import org.jetbrains.plugins.scala.codeInspection.ui.{InspectionOption, InspectionOptions}
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScMethodCall, ScReturn}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt}

import javax.swing.JComponent
import scala.annotation.tailrec

final class NonLocalReturnInspection extends AbstractRegisteredInspection {
  import NonLocalReturnInspection._

  private val checkCompilerOption = {
    val compilerOptionName = "-Xlint:nonlocal-return"
    val isCompilerOptionPresent: ScNamedElement => Boolean = {
      element => element.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains(compilerOptionName))
    }

    new InspectionOptions(
      "checkCompilerOption",
      ScalaInspectionBundle.message("nonlocal.return.check.compiler.option"),
      Seq(
        InspectionOption("", isCompilerOptionPresent),
        InspectionOption("", _ => true)
      ),
      selectedIndex = 1
    )
  }

  @Override
  override def createOptionsPanel(): JComponent =
    checkCompilerOption.checkBox

  override def writeSettings(node: Element): Unit = {
    checkCompilerOption.writeSettings(node)
    super.writeSettings(node)
  }

  override def readSettings(node: Element): Unit = {
    super.readSettings(node)
    checkCompilerOption.readSettings(node)
  }

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case function: ScFunctionDefinition if checkCompilerOption.isEnabled(function) =>
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
