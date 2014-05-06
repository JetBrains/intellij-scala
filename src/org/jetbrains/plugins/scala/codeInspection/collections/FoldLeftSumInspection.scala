package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FoldLeftSumInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new FoldLeftSum(this))
}

class FoldLeftSum(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection){

  def hint = InspectionBundle.message("foldLeft.sum.hint")

  private def checkNotString(optionalBase: Option[ScExpression]): Boolean = {
    optionalBase match {
      case Some(expr) =>
        expr.getType(TypingContext.empty).getOrAny match {
          case ScParameterizedType(_, Seq(scType)) =>
            val project = expr.getProject
            val manager = ScalaPsiManager.instance(project)
            val stringClass = manager.getCachedClass(GlobalSearchScope.allScope(project), "java.lang.String")
            if (stringClass == null) return false
            val stringType = new ScDesignatorType(stringClass)
            if (scType.conforms(stringType)) false
            else {
              val exprWithSum = ScalaPsiElementFactory.createExpressionFromText(expr.getText + ".sum", expr.getContext).asInstanceOf[ScExpression]
              exprWithSum.findImplicitParameters match {
                case Some(implPar) => true
                case _ => false
              }
            }
          case _ => false
        }
      case _ => false
    }
  }
  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (None, Some(secondRef))
        if List("foldLeft", "/:").contains(secondRef.refName) &&
                isLiteral(second.args, "0") &&
                last.args.size == 1 &&
                isSum(last.args(0)) &&
                checkResolve(secondRef, likeCollectionClasses) &&
                checkNotString(second.optionalBase) =>

        createSimplification(second, last.itself, Nil, "sum")
      case _ => Nil
    }
  }
}

