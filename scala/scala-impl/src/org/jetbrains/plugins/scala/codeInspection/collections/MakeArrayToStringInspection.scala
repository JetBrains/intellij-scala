package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MakeArrayToStringInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(MakeArrayToStringInspection)
}

object MakeArrayToStringInspection extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("format.with.mkstring")

  private val mkString = """mkString("Array(", ", ", ")")"""

  override def getSimplification(expr: ScExpression): Option[Simplification] =
    getToStringToMkStringSimplification(expr, isArray, mkString, replace)
}
