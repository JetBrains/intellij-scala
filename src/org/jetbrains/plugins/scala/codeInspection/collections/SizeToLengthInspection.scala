package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ExpressionType
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScalaType}

/**
 * @author Nikolay.Tropin
 */
class SizeToLengthInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(SizeToLength)
}

object SizeToLength extends SimplificationType {
  override def hint: String = InspectionBundle.message("size.to.length")
  val `.size` = invocation("size").from(likeCollectionClasses)

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case (qual @ ExpressionType(tpe))`.size`() if isArray(qual) || isString(tpe) =>
        Some(replace(expr).withText(invocationText(qual, "length")).highlightFrom(qual))
      case _ => None
    }
  }
  
  def isString(tp: ScType) = {
    val extracted = ScalaType.extractDesignatorSingletonType(tp).getOrElse(tp)
    val canonicalText = extracted.canonicalText
    canonicalText == "_root_.java.lang.String" || canonicalText == "_root_.scala.Predef.String"
  }
}
