package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MapGetOrElseBooleanInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(MapGetOrElseFalse, MapGetOrElseTrue)
}

abstract class MapGetOrElseBoolean(defaultValue: String, newMethodName: String, override val hint: String) extends SimplificationType {
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual `.map` (f@returnsBoolean()) `.getOrElse` (literal(`defaultValue`)) if isOption(qual) =>
        Some(replace(expr).withText(invocationText(qual, newMethodName, f)).highlightFrom(qual))
      case _ => None
    }
  }
}

object MapGetOrElseFalse extends MapGetOrElseBoolean("false", "exists", ScalaInspectionBundle.message("map.getOrElse.false.hint"))

object MapGetOrElseTrue extends MapGetOrElseBoolean("true", "forall", ScalaInspectionBundle.message("map.getOrElse.true.hint"))
