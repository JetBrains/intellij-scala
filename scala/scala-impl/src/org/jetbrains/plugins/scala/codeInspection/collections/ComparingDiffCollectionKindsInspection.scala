package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

import scala.collection.immutable.ArraySeq

class ComparingDiffCollectionKindsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(ComparingDiffCollectionKinds)
}

object ComparingDiffCollectionKinds extends SimplificationType {
  sealed trait Side {
    def fold[T](ifLeft: => T)(ifRight: => T): T = this match {
      case Side.Left => ifLeft
      case Side.Right => ifRight
    }
  }
  object Side {
    final case object Right extends Side
    final case object Left extends Side
  }

  override def hint: String = ScalaInspectionBundle.message("hint.comparing.different.collection.kinds")
  @Nls
  def convertHint(side: Side, toCollection: String): String = side match {
    case Side.Left => ScalaInspectionBundle.message("hint.convert.left.hand.side.to.collection", toCollection)
    case Side.Right => ScalaInspectionBundle.message("hint.convert.right.hand.side.to.collection", toCollection)
  }

  override def getSimplifications(expr: ScExpression): Seq[Simplification] = expr match {
    case (left @ collectionOfKind(leftKind)) `(!)==` (right @ collectionOfKind(rightKind))
      if leftKind != rightKind =>
      def convertSimplification(side: Side): Seq[Simplification] = {
        val (otherKind, exprToConvert) =
          side.fold(rightKind -> left)(leftKind -> right)
        if (otherKind == "Array") return Seq.empty
        val method = if (otherKind == "Iterator" && expr.scalaLanguageLevelOrDefault >= ScalaLanguageLevel.Scala_2_13) "iterator" else s"to$otherKind"
        val convertText = partConvertedExprText(expr, exprToConvert, method)
        Seq(replace(expr).withText(convertText).withHint(convertHint(side, otherKind)).highlightRef)
      }
      convertSimplification(Side.Left) ++ convertSimplification(Side.Right)
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
      case _: ScMethodCall | _: ScReferenceExpression | _: ScParenthesisedExpr | _: ScTuple | _: ScNamedTuple =>
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
