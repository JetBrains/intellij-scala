package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

/** Scala's special typing rule for java.lang.Object.getClass. */
private[psi] object GetClassType {
  def isGetClass(method: PsiMethod): Boolean =
    method.getName == "getClass" && Option(method.getContainingClass).exists(_.getQualifiedName == "java.lang.Object")

  def returnType(reference: ScReferenceExpression, result: ScalaResolveResult): Option[ScType] = result.element match {
    case method: PsiMethod if isGetClass(method) =>
      implicit val projectContext: ProjectContext = reference.projectContext
      reference.elementScope.getCachedClass("java.lang.Class").map { jlClass =>
        val upperBound = receiverType(reference.qualifier, result, reference).flatMap {
          case ScDesignatorType(_: ScObject) => None
          case ScCompoundType(comps, _, _) => comps.headOption.map(widen)
          case tpe => Some(tpe)
        }.getOrElse(api.Any)
        val argument = ScExistentialArgument("_$1", Nil, api.Nothing, upperBound)
        ScExistentialType(ScParameterizedType(ScDesignatorType(jlClass), Seq(argument)))
      }
    case _ => None
  }

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
