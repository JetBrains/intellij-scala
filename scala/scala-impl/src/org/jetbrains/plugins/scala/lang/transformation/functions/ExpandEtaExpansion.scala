package org.jetbrains.plugins.scala.lang.transformation
package functions

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, FirstChild, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.Function._

/**
  * @author Pavel Fatin
  */
class ExpandEtaExpansion extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScUnderscoreSection) && FirstChild(r @ ReferenceTarget(m: ScFunction)) =>
      process(e, r, clausesOf(m), typed = true)

    case (e: ScUnderscoreSection) && FirstChild(c @ ScMethodCall(ReferenceTarget(m: ScFunction), _)) =>
      process(e, c, clausesOf(m).drop(nestingLevelOf(c)), typed = true)

    case (e: ScReferenceExpression) && ReferenceTarget(m: ScFunction) &&
      NonValueType(_: ScMethodType) && ExpectedType(_: ScParameterizedType) =>
      process(e, e, clausesOf(m), typed = false)

    case (e @ ScMethodCall(ReferenceTarget(m: ScFunction), _)) &&
      NonValueType(_: ScMethodType) && ExpectedType(_: ScParameterizedType) =>
      process(e, e, clausesOf(m).drop(nestingLevelOf(e)), typed = false)

    case (e: ScUnderscoreSection) && FirstChild(r @ ReferenceTarget(p: ScParameter)) if p.isCallByNameParameter =>
      e.replace(code"() => $r")
  }

  private def clausesOf(m: ScFunction): Seq[ScParameterClause] =
    m.clauses.map(_.clauses).getOrElse(Seq.empty)

  private def nestingLevelOf(call: ScMethodCall): Int = call.getFirstChild match {
    case it: ScMethodCall => 1 + nestingLevelOf(it)
    case _ => 1
  }

  private def process(e: ScExpression, target: PsiElement, clauses: Seq[ScParameterClause], typed: Boolean)(implicit project: ProjectContext): Unit = {
    def formatParameters(clause: ScParameterClause) = {
      val list = clause.parameters
        .map(p => p.typeElement.filter(const(typed)).map(t => p.name + ": " + t.getText).getOrElse(p.name))
      if (list.length == 1 && !typed) list.mkString else list.mkString("(", ", ", ")")
    }

    def formatArguments(clause: ScParameterClause) =
      clause.parameters.map(_.name).mkString("(", ", ", ")")

    val parameters = clauses.map(formatParameters)
    val arguments = clauses.map(formatArguments)

    val declarations = if (arguments.nonEmpty) parameters.mkString(" => ") else "()"
    val applications = if (arguments.nonEmpty) Some(arguments.mkString) else None

    e.replace(code"$declarations => $target$applications")
  }
}
