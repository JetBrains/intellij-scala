package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
 * @author Nikolay.Tropin
 */
class SizeToLengthInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(SizeToLength)
}

object SizeToLength extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("size.to.length")
  val `.size`: Qualified = invocation("size").from(likeCollectionClasses)

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.size`(qual@Typeable(tpe)) if isArray(qual) || isString(tpe) =>
        Some(replace(expr).withText(invocationText(qual, "length")).highlightFrom(qual))
      case _ => None
    }
  }
  
  def isString(tp: ScType): Boolean = {
    val extracted = tp.tryExtractDesignatorSingleton
    val canonicalText = extracted.canonicalText
    canonicalText == "_root_.java.lang.String" || canonicalText == "_root_.scala.Predef.String"
  }
}
