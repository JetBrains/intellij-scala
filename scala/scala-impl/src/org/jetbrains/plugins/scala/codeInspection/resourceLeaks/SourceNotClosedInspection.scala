package org.jetbrains.plugins.scala.codeInspection.resourceLeaks

import com.intellij.codeInspection.{ProblemHighlightType, _}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.collections.{MethodRepr, Qualified, invocation, unqualifed}
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, InspectionBundle, _}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

class SourceNotClosedInspection extends AbstractRegisteredInspection {

  override def problemDescriptor(element: PsiElement,
                                 maybeQuickFix: Option[LocalQuickFix],
                                 descriptionTemplate: String,
                                 highlightType: ProblemHighlightType)
                                (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case MethodRepr(_, Some(SourceCreatingMethod(_)), _, _) && NonClosingMethodOfSource() =>
        super.problemDescriptor(element, maybeQuickFix, InspectionBundle.message("source.not.closed"), highlightType)

      case SourceCreatingMethod(expr) if expressionResultIsNotUsed(expr) =>
        super.problemDescriptor(element, maybeQuickFix, InspectionBundle.message("source.not.closed"), highlightType)

      case _ =>
        None
    }
  }

  private object NonClosingMethodOfSource {
    private def sourceNonClosingMethodNames = Set(
      "ch", "descr", "getLines", "hasNext", "mkString", "nerrors", "next",
      "nwarnings", "pos", "report", "reportError", "reportWarning", "reset"
    )
    private val sourceNonClosingMethods =
      invocation(sourceNonClosingMethodNames).from(Array("scala.io.Source", "scala.io.BufferedSource"))

    // in the listed base classes, everything will not close the Source
    private val nonClosingBaseClasses = Array(
      "scala.Any", "scala.AnyRef", "scala.collection.Iterator",
      "scala.collection.TraversableOnce", "scala.collection.GenTraversableOnce",
      "scala.collection.IterableOnceOps"
    )
    private val baseClassesNonClosingMethods = new Qualified(_ => true).from(nonClosingBaseClasses)

    def unapply(expr: ScExpression): Boolean = expr match {
      case sourceNonClosingMethods(_, _*) | baseClassesNonClosingMethods(_, _*) => true
      case _ => false
    }
  }

  private object SourceCreatingMethod {
    private val qualFrom = invocation(Set("fromFile", "fromURL", "fromURI")).from(Array("scala.io.Source"))
    private val unqualFrom = unqualifed(Set("fromFile", "fromURL", "fromURI")).from(Array("scala.io.Source"))

    def unapply(expr: ScExpression): Option[ScExpression] = expr match {
      case qualFrom(_, _*) | unqualFrom(_*) =>
        Some(expr)
      case _ => None
    }
  }
}
