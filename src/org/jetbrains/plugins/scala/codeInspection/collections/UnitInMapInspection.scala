package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{ChangeReferenceNameQuickFix, InspectionBundle, ProblemsHolderExt}
import org.jetbrains.plugins.scala.extensions.ExpressionType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScTypeExt, Unit}

/**
 * @author Nikolay.Tropin
 */
class UnitInMapInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array()

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(call, _, Some(ref), Seq(arg @ lambdaWithBody(body)))
      if ref.refName == "map" && checkResolve(ref, getLikeCollectionClasses) =>

      val isInBlock = call.getParent match {
        case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions | _: ScalaFile => true
        case _ => false
      }
      val fixes =
        if (isInBlock) Seq(new ChangeReferenceNameQuickFix(InspectionBundle.message("use.foreach.instead.of.map"), ref, "foreach"))
        else Seq.empty
      implicit val typeSystem = holder.typeSystem
      val unitTypeReturns = body.calculateReturns().collect {
        case expr @ ExpressionType(ft @ ScFunctionType(Unit, _)) if arg.getType().getOrAny.equiv(ft) => expr
        case expr @ ExpressionType(Unit) => expr
      }.filter(_.getTextLength > 0)

      unitTypeReturns.foreach { e =>
        if (e.isPhysical)
          holder.registerProblem(e, InspectionBundle.message("expression.unit.return.in.map"), highlightType, fixes: _*)
      }
  }

  object lambdaWithBody {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      expr match {
        case ScBlock(ScFunctionExpr(_, res)) => res
        case ScFunctionExpr(_, res) => res
        case e => Some(e)
      }
    }
  }
}