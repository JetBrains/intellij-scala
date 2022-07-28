package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MapGetGetInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(MapGetGet)
}

object MapGetGet extends SimplificationType() {

  override def hint: String = ScalaInspectionBundle.message("get.get.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.get`(map`.getOnMap`(key)) =>
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
