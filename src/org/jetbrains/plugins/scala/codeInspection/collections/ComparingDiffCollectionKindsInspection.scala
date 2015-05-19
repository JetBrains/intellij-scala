package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * @author Nikolay.Tropin
 */
class ComparingDiffCollectionKindsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ComparingDiffCollectionKinds)
}

object ComparingDiffCollectionKinds extends SimplificationType {
  override def hint: String = "Comparing different collection kinds"
  def convertHint(side: String, toCollection: String) = s"Convert $side-hand side to $toCollection"

  override def getSimplifications(expr: ScExpression): Seq[Simplification] = expr match {
    case (left @ collectionOfKind(leftKind)) `(!)==` (right @ collectionOfKind(rightKind))
      if leftKind != rightKind =>
      def convertSimplification(leftSide: Boolean): Seq[Simplification] = {
        val (otherKind, exprToConvert, side) =
          if (leftSide) (rightKind, left, "left")
          else (leftKind, right, "right")
        if (otherKind == "Array") return Seq.empty
        val convertText = partConvertedExprText(expr, exprToConvert, "to" + otherKind)
        Seq(replace(expr).withText(convertText).withHint(convertHint(side, otherKind)).highlightRef)
      }
      convertSimplification(leftSide = true) ++ convertSimplification(leftSide = false)
    case _ => Seq.empty
  }

  private object collectionOfKind {
    def unapply(expr: ScExpression): Option[String] = {
      expr match {
        case _ if isSeq(expr) => Some("Seq")
        case _ if isSet(expr) => Some("Set")
        case _ if isMap(expr) => Some("Map")
        case _ if isIterator(expr) => Some("Iterator")
        case _ if isArray(expr) => Some("Array")
        case _ => None
      }
    }
  }

  private object `(!)==` {
    def unapply(expr: ScExpression): Option[(ScExpression, ScExpression)] = {
      expr match {
        case left `==` right => Some(left, right)
        case left `!=` right => Some(left, right)
        case _ => None
      }
    }
  }

  private def partConvertedExprText(expr: ScExpression, subExpr: ScExpression, conversion: String) = {
    val subExprConvertedText = subExpr match {
      case _: ScMethodCall | _: ScReferenceExpression | _: ScParenthesisedExpr | _: ScTuple => 
        s"${subExpr.getText}.$conversion" 
      case _ => s"(${subExpr.getText}).$conversion"
    }
    val exprText = expr.getText
    val rangeInParent = subExpr.getTextRange.shiftRight( - expr.getTextOffset)
    val firstPart = exprText.substring(0, rangeInParent.getStartOffset)
    val lastPart = exprText.substring(rangeInParent.getEndOffset)
    s"$firstPart$subExprConvertedText$lastPart"
  }
}
