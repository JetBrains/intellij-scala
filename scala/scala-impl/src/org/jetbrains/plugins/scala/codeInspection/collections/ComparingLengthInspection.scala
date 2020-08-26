package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
final class ComparingLengthInspection extends OperationOnCollectionInspection {

  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ComparingLengthInspection.ComparingLength)
}

object ComparingLengthInspection {

  val hint: String = ScalaInspectionBundle.message("replace.with.lengthCompare")

  private val ComparingLength: SimplificationType = new SimplificationType() {
    override def hint: String = ComparingLengthInspection.hint

    override def getSimplification(e: ScExpression): Option[Simplification] = Some(e).collect {
      // TODO infix notation?
      case `.sizeOrLength`(q) `>` n => (q, ">", n)
      case `.sizeOrLength`(q) `>=` n => (q, ">=", n)
      case `.sizeOrLength`(q) `==` n => (q, "==", n)
      case `.sizeOrLength`(q) `!=` n => (q, "!=", n)
      case `.sizeOrLength`(q) `<` n => (q, "<", n)
      case `.sizeOrLength`(q) `<=` n => (q, "<=", n)
    } filter { case (q, _, n) =>
      isNonIndexedSeq(q) && !isZero(n)
    } map { case (q, op, n) =>
      replace(e).withText(s"${invocationText(q, "lengthCompare", n)} $op 0").highlightFrom(q)
    }
  }

  private def isZero(expression: ScExpression): Boolean = expression match {
    case ScIntegerLiteral(0) => true
    case _ => false
  }
}