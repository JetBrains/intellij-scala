package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

import scala.collection.immutable.ArraySeq

final class ComparingLengthInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(ComparingLengthInspection.ComparingLength)
}

object ComparingLengthInspection {

  val hintLengthCompare: String = ScalaInspectionBundle.message("replace.with.lengthCompare")
  val hintSizeIs: String = ScalaInspectionBundle.message("replace.with.sizeIs")

  private val ComparingLength: SimplificationType = new SimplificationType() {
    override def hint: String = null

    override def getSimplification(e: ScExpression): Option[Simplification] = Some(e).collect {
      // TODO infix notation?
      case `.sizeOrLength`(q) `>` n => (q, ">", n)
      case `.sizeOrLength`(q) `>=` n => (q, ">=", n)
      case `.sizeOrLength`(q) `==` n => (q, "==", n)
      case `.sizeOrLength`(q) `!=` n => (q, "!=", n)
      case `.sizeOrLength`(q) `<` n => (q, "<", n)
      case `.sizeOrLength`(q) `<=` n => (q, "<=", n)
    }.filter { case (q, _, n) =>
      isNonIndexedSeq(q) && !isZero(n)
    }.map { case (q, op, n) =>
      val repl = replace(e).highlightFrom(q)
      if (doesScalaHasSizeIs(e)) repl.withText(s"${invocationText(q, "sizeIs")} $op ${n.getText}").withHint(hintSizeIs)
      else repl.withText(s"${invocationText(q, "lengthCompare", n)} $op 0").withHint(hintLengthCompare)
    }
  }

  private def isZero(expression: ScExpression): Boolean = expression match {
    case ScIntegerLiteral(0) => true
    case _ => false
  }

  private def doesScalaHasSizeIs(e: ScExpression): Boolean = {
    println(e)
    e.scalaLanguageLevel
      .forall(_ >= ScalaLanguageLevel.Scala_2_13)
  }
}