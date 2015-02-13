package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * @author Nikolay.Tropin
 */
class ZipWithIndex(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def hint: String = InspectionBundle.message("replace.with.zipWithIndex")

  override def getSimplification(single: MethodRepr): List[Simplification] = {
    (single.optionalBase, single.optionalMethodRef, single.args) match {
      case (Some(ResolvesTo(x)), Some(ref), Seq(MethodRepr(_, Some(ResolvesTo(y)), Some(argRef), Seq())))
        if ref.refName == "zip" && argRef.refName == "indices" &&
        isCollectionMethod(ref) && isCollectionMethod(argRef) &&
        x == y && !x.isInstanceOf[PsiMethod] =>

        createSimplification(single, single.itself, "zipWithIndex", Seq.empty)
      case _ => Nil
    }
 }
}

class ZipWithIndexInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(new ZipWithIndex(this))
}