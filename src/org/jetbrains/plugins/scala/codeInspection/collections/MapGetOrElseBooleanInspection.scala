package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * Nikolay.Tropin
  * 2014-05-05
  */
class MapGetOrElseBooleanInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(MapGetOrElseFalse, MapGetOrElseTrue)
}

abstract class MapGetOrElseBoolean(defaultValue: String, newMethodName: String, hintKey: String) extends SimplificationType() {
  def hint: String = InspectionBundle.message(hintKey)

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual `.map` (f@returnsBoolean()) `.getOrElse` (literal(`defaultValue`)) if isOption(qual) =>
        Some(replace(expr).withText(invocationText(qual, newMethodName, f)).highlightFrom(qual))
      case _ => None
    }
  }
}

object MapGetOrElseFalse extends MapGetOrElseBoolean("false", "exists", "map.getOrElse.false.hint")

object MapGetOrElseTrue extends MapGetOrElseBoolean("true", "forall", "map.getOrElse.true.hint")
