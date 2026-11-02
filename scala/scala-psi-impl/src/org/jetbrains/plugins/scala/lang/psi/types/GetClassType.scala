package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/** Scala's special typing rule for java.lang.Object.getClass. */
private[psi] object GetClassType {
  def isGetClass(method: PsiMethod): Boolean =
    method.getName == "getClass" && Option(method.getContainingClass).exists(_.getQualifiedName == "java.lang.Object")

  def receiverType(receiver: Option[ScExpression], result: ScalaResolveResult, place: PsiElement): Option[ScType] = {
    val explicitReceiver = receiver.orElse {
      place match {
        case reference: ScReferenceExpression => reference.getContext match {
          case sugar: ScSugarCallExpr if sugar.operation == reference => Some(sugar.getBaseExpr)
          case _ => None
        }
        case _ => None
      }
    }
    explicitReceiver.flatMap(_.getTypeWithoutImplicits(ignoreBaseType = true).toOption)
      .orElse(result.fromType)
      .orElse(ScalaPsiUtil.drvTemplate(place).flatMap(_.`type`().toOption))
      .map(widen)
  }

  private def widen(tpe: ScType): ScType = tpe.widen match {
    case ScThisType(cls) => cls.getTypeWithProjections(thisProjections = true).getOrElse(tpe)
    case widened => widened
  }
}
