package org.jetbrains.plugins.scala.codeInspection.resourceLeaks

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.codeInspection._
import org.jetbrains.plugins.scala.codeInspection.collections.{MethodRepr, Qualified, invocation, unqualifed}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class SourceNotClosedInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case element@MethodRepr(_, Some(SourceCreatingMethod(_)), _, _) & NonClosingMethodOfSource() =>
      holder.registerProblem(element, ScalaInspectionBundle.message("source.not.closed"))

    case element@SourceCreatingMethod(expr) if expressionResultIsNotUsed(expr) =>
      holder.registerProblem(element, ScalaInspectionBundle.message("source.not.closed"))

    case _ =>
  }

  private object NonClosingMethodOfSource {
    private def sourceNonClosingMethodNames = Set(
      "ch", "descr", "getLines", "hasNext", "mkString", "nerrors", "next",
      "nwarnings", "pos", "report", "reportError", "reportWarning", "reset"
    )

    private val sourceNonClosingMethods =
      invocation(sourceNonClosingMethodNames).from(ArraySeq("scala.io.Source", "scala.io.BufferedSource"))

    // in the listed base classes, everything will not close the Source
    private val nonClosingBaseClasses = ArraySeq(
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
    private val qualFrom = invocation(Set("fromFile", "fromURL", "fromURI")).from(ArraySeq("scala.io.Source"))
    private val unqualFrom = unqualifed(Set("fromFile", "fromURL", "fromURI")).from(ArraySeq("scala.io.Source"))

    def unapply(expr: ScExpression): Option[ScExpression] = expr match {
      case qualFrom(_, _*) | unqualFrom(_*) =>
        Some(expr)
      case _ => None
    }
  }
}
