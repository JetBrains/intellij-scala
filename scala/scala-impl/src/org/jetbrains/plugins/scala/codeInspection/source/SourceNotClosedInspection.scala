package org.jetbrains.plugins.scala.codeInspection.source

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil._
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition, ScReferenceExpression}

class SourceNotClosedInspection extends AbstractInspection("ScalaSourceNotClosed", "Source not closed") {

  private val sourceMethodNames = Seq("fromFile", "fromURI", "fromURL", "fromInputStream")

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr@(getLinesorMkString(source@SourceFrom(_))) if isExpressionOfType("scala.io.Source", source) =>
      registerSourceNotClosed(holder, expr)
  }

  private def registerSourceNotClosed(holder: ProblemsHolder, expr: PsiElement): Unit = {
    holder.registerProblem(expr, "Source not closed", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }

  object SourceFrom {
    def unapply(expr: ScExpression): Option[ScExpression] = methodNameMatches(expr, ref => isSourceFromMethod(ref))

    private def isSourceFromMethod(ref: ScReferenceExpression) = sourceMethodNames.contains(ref.refName)
  }

  object getLinesorMkString {
    def unapply(expr: ScExpression): Option[ScExpression] = methodNameMatches(expr, ref => ref.refName == "getLines" || ref.refName == "mkString")
  }

  private def methodNameMatches(expr: ScExpression, methodNameCheck: ScReferenceExpression => Boolean): Option[ScExpression] = {
    expr match {
      case MethodRepr(_, Some(qual), Some(ref), args) if methodNameCheck(ref) =>
        args match {
          case Seq(arg: ScNewTemplateDefinition) if isExpressionOfType("java.io.InputStream", arg) => Some(qual)
          case Seq(arg) if isExpressionOfType("java.io.InputStream", arg) => None
          case Seq(_*) => Some(qual)
          case Nil => Some(qual)
        }
      case _ => None
    }
  }
}