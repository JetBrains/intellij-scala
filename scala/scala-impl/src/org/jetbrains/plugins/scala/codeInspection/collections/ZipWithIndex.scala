package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
object ZipWithIndex extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("replace.with.zipWithIndex")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case (ref @ ResolvesTo(x))`.zip`((ResolvesTo(y))`.indices`Seq()) if x == y && !x.isInstanceOf[PsiMethod] =>
        Some(replace(expr).withText(invocationText(ref, "zipWithIndex")))
      case _ => None
    }
 }
}

class ZipWithIndexInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ZipWithIndex)
}
