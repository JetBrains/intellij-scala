package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall}

import scala.collection.immutable.ArraySeq

class SimulatedFilterInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(SimulatedFilterOption)
}

object SimulatedFilterOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.simulated.filter")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case ex@IfStmt(`&&`(`.isDefined`(optDef), ScMethodCall(pred, Seq(`.get`(optGet)))), optThen, scalaNone()) =>
      replaceIfEqual(ex, pred, optDef, optGet, optThen)
    case _ => None
  }

  private def replaceIfEqual(expression: ScExpression,
                             pred: ScExpression,
                             optDef: ScExpression,
                             optGet: ScExpression,
                             optThen: ScExpression): Option[Simplification] = {
    if (areElementsEquivalent(optDef, optGet) && areElementsEquivalent(optDef, optThen))
      Some(replace(expression).withText(s"${optDef.getText}.filter(${pred.getText})"))
    else
      None
  }
}
