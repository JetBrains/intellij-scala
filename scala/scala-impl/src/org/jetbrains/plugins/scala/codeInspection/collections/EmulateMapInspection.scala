package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.EmulateMapInspection._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

/**
  * @author t-kameyama
  */
class EmulateMapInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(EmulateMap)
}

private object EmulateMapInspection {

  private val EmulateMap = new SimplificationType {
    override def hint: String = InspectionBundle.message("replace.with.map")

    override def getSimplification(expr: ScExpression): Option[Simplification] = Some(expr).collect {
      case qual `.foldLeft` (start, FoldLeftOp(f)) => (qual, start, f)
      case qual `.foldRight` (start, FoldRightOp(f)) => (qual, start, f)
    } filter { case (qual, start, _) =>
      isTarget(qual, start)
    } map { case (qual, _, f) =>
      replace(expr).withText(invocationText(qual, "map", f)).highlightFrom(qual)
    }

    private def isTarget(qual: ScExpression, start: ScExpression) = {
      def isSameType = qual match {
        case (Typeable(qualType)) => (qualType.tryExtractDesignatorSingleton, start.`type`().getOrAny) match {
          case (ParameterizedType(type1, _), ParameterizedType(type2, _)) if type1.equiv(type2) => true
          case _ => false
        }
        case _ => false
      }

      def isEmptyValue = start match {
        case ScGenericCall(ScReferenceExpression(f: ScFunctionDefinition), _) if f.name == "empty" => true
        case _ => false
      }

      isSeq(qual) && isSameType && isEmptyValue
    }

    private object FoldLeftOp {
      def unapply(expr: ScExpression): Option[ScExpression] = expr match {
        case ScFunctionExpr(Seq(acc, x), Some(ScInfixExpr(ScReferenceExpression(acc2), op, FunctionCall(f, x2))))
          if op.getText == ":+" &&
            PsiEquivalenceUtil.areElementsEquivalent(acc, acc2) &&
            PsiEquivalenceUtil.areElementsEquivalent(x, x2) => Option(f)
        case _ => None
      }
    }

    private object FoldRightOp {
      def unapply(expr: ScExpression): Option[ScExpression] = expr match {
        case ScFunctionExpr(Seq(x, acc), Some(ScInfixExpr(FunctionCall(f, x2), op, ScReferenceExpression(acc2))))
          if op.getText == "+:" &&
            PsiEquivalenceUtil.areElementsEquivalent(acc, acc2) &&
            PsiEquivalenceUtil.areElementsEquivalent(x, x2) => Option(f)
        case _ => None
      }
    }

    private object FunctionCall {
      def unapply(expr: ScExpression): Option[(ScExpression, PsiElement)] = expr match {
        case ScMethodCall(f, Seq(ScReferenceExpression(x))) => Option((f, x))
        case _ => None
      }
    }

  }

}

