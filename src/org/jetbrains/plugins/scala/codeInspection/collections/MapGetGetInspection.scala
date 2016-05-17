package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class MapGetGetInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(MapGetGet)
}

object MapGetGet extends SimplificationType() {

  def hint = InspectionBundle.message("get.get.hint")

  override def getSimplification(expr: ScExpression) = {
    expr match {
      case map`.getOnMap`(key)`.get`() =>
        Some(replace(expr)
          .withText(replacementText(map, key))
          .highlightFrom(map))
      case _ => None
    }
  }

  def replacementText(qual: ScExpression, keyArg: ScExpression): String = {
    val firstArgText = argListText(Seq(keyArg))
    s"${qual.getText}$firstArgText"
  }
}
