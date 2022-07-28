package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class SomeToOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(SomeToOptionInspection)
}

object SomeToOptionInspection extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.option")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case `scalaSome`(e) => Some(replace(expr).withText(s"Option(${e.getText})").highlightFrom(expr))
    case _ => None
  }
}