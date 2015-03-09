package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ExpressionType
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Nikolay.Tropin
 */
class ZeroIndexToHeadInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ZeroIndexToHead)
}

object ZeroIndexToHead extends SimplificationType() {
  override def hint: String = InspectionBundle.message("replace.with.head")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    val genSeqType = ScalaPsiElementFactory.createTypeElementFromText("scala.collection.GenSeq[_]", expr.getContext, expr).calcType
    expr match {
      case (qual @ ExpressionType(tp))`.apply`(literal("0")) if tp.conforms(genSeqType) =>
        Some(replace(expr).withText(invocationText(qual, "head")).highlightFrom(qual))
      case _ => None
    }
  }
}
