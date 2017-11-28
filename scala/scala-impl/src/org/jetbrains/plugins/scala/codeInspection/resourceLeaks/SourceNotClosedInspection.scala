package org.jetbrains.plugins.scala.codeInspection.resourceLeaks

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.collections.{invocation, unqualifed}
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition


class SourceNotClosedInspection extends AbstractInspection("SourceNotClosed", "Source not closed") {
  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case ref @ ScReferenceExpression.withQualifier(scalaIoFromPathLike()) =>
      val problemElement = ref.getParent match {
        case call @ ScMethodCall(r, _) if r eq ref => call
        case _ => ref.getOriginalElement
      }

      markProblems(ref, problemElement)
  }

  private[this] def markProblems(ref: ScReferenceExpression, problemElement: PsiElement)(implicit holder: ProblemsHolder): Unit = {
    ref.resolve() match {
      case funDef: ScFunctionDefinition if nonClosingMethods contains funDef.name =>
        holder.registerProblem(problemElement, InspectionBundle.message("source.not.closed"),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      case _ =>
    }
  }

  private[this] val nonClosingMethods = Set("mkString", "getLines")

  private[this] val qualFromFile = invocation("fromFile").from(Array("scala.io.Source"))
  private[this] val unqualFromFile = unqualifed("fromFile").from(Array("scala.io.Source"))
  private[this] val qualFromURL = invocation("fromURL").from(Array("scala.io.Source"))
  private[this] val unqualFromURL = unqualifed("fromURL").from(Array("scala.io.Source"))
  private[this] val qualFromURI = invocation("fromURI").from(Array("scala.io.Source"))
  private[this] val unqualFromURI = unqualifed("fromURI").from(Array("scala.io.Source"))

  private[this] object scalaIoFromPathLike {
    def unapply(expr: ScExpression): Boolean = expr match {
      case _ qualFromFile(_) => true
      case unqualFromFile(_) => true
      case _ qualFromURI(_) => true
      case unqualFromURI(_) => true
      case _ qualFromURL(_) => true
      case unqualFromURL(_) => true
      case _ => false
    }
  }
}
