package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

object ZipWithIndexInspection extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("replace.with.zipWithIndex")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case (ref @ ResolvesTo(x))`.zip`(`.indices`(ResolvesTo(y))) if x == y && !x.is[PsiMethod] =>
        Some(replace(expr).withText(invocationText(ref, "zipWithIndex")))
      case _ => None
    }
 }
}

class ZipWithIndexInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(ZipWithIndexInspection)
}
