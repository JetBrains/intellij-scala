package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
object ZipWithIndex extends SimplificationType() {
  override def hint: String = InspectionBundle.message("replace.with.zipWithIndex")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case (ref @ ResolvesTo(x))`.zip`((ref2 @ ResolvesTo(y))`.indices`()) if x == y && !x.isInstanceOf[PsiMethod] =>
        Some(replace(expr).withText(invocationText(ref, "zipWithIndex")))
      case _ => None
    }
 }
}

class ZipWithIndexInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ZipWithIndex)
}