package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ExpressionType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScParameterizedType, ScType}

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
      case (qual @ ExpressionType(tpe))`.size`() if isArray(tpe) || isString(tpe) =>
        Some(replace(expr).withText(invocationText(qual, "length", Seq.empty)).highlightFrom(qual))
      case _ => None
    }
  }
  
  def isString(tp: ScType) = {
    val extracted = ScType.extractDesignatorSingletonType(tp).getOrElse(tp)
    val canonicalText = extracted.canonicalText
    canonicalText == "_root_.java.lang.String" || canonicalText == "_root_.scala.Predef.String"
  }
  
  def isArray(tp: ScType) = ScType.extractDesignatorSingletonType(tp).getOrElse(tp) match {
    case ScParameterizedType(ScDesignatorType(ClassQualifiedName("scala.Array")), _) => true
    case _ => false
  }
}
