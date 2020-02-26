package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class HeadOrLastOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(IfElseToHeadOption, IfElseToLastOption, LiftToHeadOption, LiftToLastOption)
}

object IfElseToHeadOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.headOption")
  override def description: String = ScalaInspectionBundle.message("ifstmt.to.headOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case IfStmt(CheckIsEmpty(coll, _, _), scalaNone(), scalaSome(coll2`.head`()))
      if PsiEquivalenceUtil.areElementsEquivalent(coll, coll2) =>
      Some(replace(expr).withText(invocationText(coll, "headOption")).highlightAll)
    case IfStmt(CheckNonEmpty(coll, _, _), scalaSome(coll2`.head`()), scalaNone())
      if PsiEquivalenceUtil.areElementsEquivalent(coll, coll2) =>
      Some(replace(expr).withText(invocationText(coll, "headOption")).highlightAll)
    case _ => None
  }
}

object IfElseToLastOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.lastOption")
  override def description: String = ScalaInspectionBundle.message("ifstmt.to.lastOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case IfStmt(CheckIsEmpty(coll, _, _), scalaNone(), scalaSome(coll2`.last`()))
      if PsiEquivalenceUtil.areElementsEquivalent(coll, coll2) =>
      Some(replace(expr).withText(invocationText(coll, "lastOption")).highlightAll)
    case IfStmt(CheckNonEmpty(coll, _, _), scalaSome(coll2`.last`()), scalaNone())
      if PsiEquivalenceUtil.areElementsEquivalent(coll, coll2) =>
      Some(replace(expr).withText(invocationText(coll, "lastOption")).highlightAll)
    case _ => None
  }
}

object LiftToHeadOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.headOption")
  override def description: String = ScalaInspectionBundle.message("lift.to.headOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case (coll`.lift`())`.apply`(literal("0")) if isSeq(coll) || isArray(coll) =>
      Some(replace(expr).withText(invocationText(coll, "headOption")).highlightFrom(coll))
    case _ => None
  }
}

object LiftToLastOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.lastOption")
  override def description: String = ScalaInspectionBundle.message("lift.to.lastOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case (coll`.lift`())`.apply`(coll2`.sizeOrLength`() `-` literal("1"))
      if PsiEquivalenceUtil.areElementsEquivalent(coll, coll2) && (isSeq(coll) || isArray(coll)) =>
      Some(replace(expr).withText(invocationText(coll, "lastOption")).highlightFrom(coll))
    case _ => None
  }
}