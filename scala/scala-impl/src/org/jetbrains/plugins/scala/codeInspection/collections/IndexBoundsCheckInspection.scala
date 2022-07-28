package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.codeInsight.PsiEquivalenceUtil.areElementsEquivalent
import org.jetbrains.plugins.scala.codeInspection.collections.IndexBoundsCheckInspection._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class IndexBoundsCheckInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(IndexBoundsCheck)
}

private object IndexBoundsCheckInspection {

  private val IndexBoundsCheck: SimplificationType = new SimplificationType {

    override def hint: String = ScalaInspectionBundle.message("ifstmt.to.lift")

    override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
      case IfStmt(IndexLessThanLengthCondition(seq1, index1), Then(seq2, index2), scalaNone())
        if areElementsEquivalent(seq1, seq2) && areElementsEquivalent(index1, index2) =>
        createSimplification(expr, seq1, index1)

      case IfStmt(IndexGreaterEqualsLengthCondition(seq1, index1), scalaNone(), Then(seq2, index2))
        if areElementsEquivalent(seq1, seq2) && areElementsEquivalent(index1, index2) =>
        createSimplification(expr, seq1, index1)

      case _ => None
    }

    private def createSimplification(expr: ScExpression, seq: ScExpression, index: ScExpression): Option[Simplification] = {
      Some(replace(expr).withText(invocationText(seq, "lift", index)).highlightFrom(expr))
    }

    private object IndexLessThanLengthCondition {
      def unapply(expr: ScExpression): Option[(ScExpression, ScExpression)] = expr match {
        // TODO infix notation?
        case index `<` `.sizeOrLength`(seq) if isSeq(seq) => Some(seq, index)
        case `.sizeOrLength`(seq) `>` index if isSeq(seq) => Some(seq, index)
        case _ => None
      }
    }

    private object IndexGreaterEqualsLengthCondition {
      def unapply(expr: ScExpression): Option[(ScExpression, ScExpression)] = expr match {
        // TODO infix notation?
        case index `>=` `.sizeOrLength`(seq) if isSeq(seq) => Some(seq, index)
        case `.sizeOrLength`(seq) `<=` index if isSeq(seq) => Some(seq, index)
        case _ => None
      }
    }

    private object Then {
      def unapply(expr: ScExpression): Option[(ScExpression, ScExpression)] = expr match {
        case scalaSome(seq `.apply` (index)) if isSeq(seq) => Some(seq, index)
        case _ => None
      }
    }

  }

}
