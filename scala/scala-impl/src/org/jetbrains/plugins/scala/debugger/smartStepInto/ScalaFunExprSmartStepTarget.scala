package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.util.Range
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext

import javax.swing.Icon

class ScalaFunExprSmartStepTarget(val funExpr: ScExpression, val stmts: Seq[ScBlockStatement], label: String, expressionLines: Range[Integer])
        extends SmartStepTarget(label, funExpr, true, expressionLines) {

  override def getIcon: Icon = Icons.LAMBDA
}

object ScalaFunExprSmartStepTarget {
  def unapply(target: ScalaFunExprSmartStepTarget): Some[(ScExpression, Seq[ScBlockStatement])] = Some((target.funExpr, target.stmts))
}

object FunExpressionTarget {
  def unapply(expr: ScExpression): Option[(Seq[ScBlockStatement], String)] = {
    expr match {
      case e if ScUnderScoreSectionUtil.isUnderscoreFunction(e) => Some(Seq(e), text(e))
      case f: ScFunctionExpr => Some(f.result.toSeq.flatMap(blockStmts), text(f))
      case b: ScBlockExpr if b.isPartialFunction =>
        val clauses = b.caseClauses.get
        Some(clauses.caseClauses.flatMap(_.expr).flatMap(blockStmts), text(b))
      case expr: ScExpression if ScalaPsiUtil.isByNameArgument(expr) => Some(blockStmts(expr), text(expr))
      case _ => None
    }
  }

  private def blockStmts(expr: ScExpression): Seq[ScBlockStatement] = {
    expr match {
      case b: ScBlock => b.statements
      case e => Seq(e)
    }
  }

  private def parameterNameAndType(expr: ScExpression): Option[String] = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(expr)
    ScalaPsiUtil.parameterOf(expr) match {
      case Some(p) if p.isByName => Some(s"${p.name}: => ${p.paramType.presentableText}")
      case Some(p) => Some(s"${p.name}: ${p.paramType.presentableText}")
      case _ => None
    }
  }

  private def shorten(s: String): String = {
    val trimmed = s.stripPrefix("{").stripSuffix("}").trim
    val lines = trimmed.linesIterator.toList
    if (lines.size > 1) lines.head + " ..."
    else trimmed
  }

  private def text(e: ScExpression) = parameterNameAndType(e).getOrElse(shorten(e.getText))
}