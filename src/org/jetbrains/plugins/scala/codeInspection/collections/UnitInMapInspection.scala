package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ChangeReferenceNameQuickFix}
import org.jetbrains.plugins.scala.extensions.ExpressionType
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScParameterizedType, Unit}

/**
 * @author Nikolay.Tropin
 */
class UnitInMapInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array()

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(call, _, Some(ref), Seq(lambdaExpressionBody(body)))
      if ref.refName == "map" && OperationOnCollectionsUtil.checkResolve(ref, likeCollectionClasses) =>

      val isInBlock = call.getParent match {
        case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions => true
        case _ => false
      }
      val fixes =
        if (isInBlock) Seq(new ChangeReferenceNameQuickFix(InspectionBundle.message("use.foreach.instead.of.map"), ref, "foreach"))
        else Seq.empty
      val unitTypeReturns = body.calculateReturns().collect {
        case expr @ ExpressionType(ft @ ScFunctionType(Unit, _)) if body.getType().getOrAny.equiv(ft) => expr
        case expr @ ExpressionType(Unit) => expr
      }
      unitTypeReturns.foreach { e =>
        holder.registerProblem(e, InspectionBundle.message("expression.unit.return.in.map"), highlightType, fixes: _*)
      }
  }

  object lambdaExpressionBody {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      expr match {
        case ScBlock(ScFunctionExpr(_, res)) => res
        case ScFunctionExpr(_, res) => res
        case e => Some(e)
      }
    }
  }
}