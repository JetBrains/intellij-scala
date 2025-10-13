package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMatch, ScMethodCall}

import scala.collection.immutable.ArraySeq

class SimulatedFilterInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(SimulatedFilterOption)
}

object SimulatedFilterOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.simulated.filter")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case ex@IfStmt(`.isDefined`(optDef) `&&` ScMethodCall(pred, Seq(`.get`(optGet))), optThen, scalaNone()) =>
      replaceIfEqual(ex, pred, optDef, Seq(optDef, optGet, optThen))
    case ex@optExpr ScMatch Seq(CaseClause(scalaSomePattern(optRef: ScReferencePattern), Guard(ScMethodCall(pred, Seq(optCall))), scalaSome(optRes)),
                                CaseClause(_: ScWildcardPattern, None, scalaNone())) if optCall.textMatches(optRef.name) =>
      replaceIfEqual(ex, pred, optExpr, Seq(optCall, optRes))
    case _ => None
  }

  private def replaceIfEqual(expression: ScExpression,
                             pred: ScExpression,
                             opt: ScExpression,
                             opts: Seq[ScExpression]): Option[Simplification] = {
    if (opts.tail.forall(areElementsEquivalent(opts.head, _)))
      Some(replace(expression).withText(s"${opt.getText}.filter(${pred.getText})"))
    else
      None
  }
}
