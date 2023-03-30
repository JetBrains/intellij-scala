package org.jetbrains.plugins.scala.codeInspection.internal

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiDocCommentOwner, PsiElement}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.internal.ApiStatusInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

class ApiStatusInspection extends LocalInspectionTool {

  private def elementsAreInTheSameModule(e1: PsiElement, e2: PsiElement): Boolean =
    ModuleUtilCore.findModuleForPsiElement(e1) == ModuleUtilCore.findModuleForPsiElement(e2)

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case fun: ScFunction =>
      fun.superMethod.collectFirst {
        case Status(status) & sup if !elementsAreInTheSameModule(fun, sup) =>
          val name = fun.name
          holder.registerProblem(fun.nameId, ScalaInspectionBundle.message("super.method.name.is.marked.as.status", name, status))
      }
    case ref: ScReference if !ScalaPsiUtil.isInsideImportExpression(ref) =>
      ref match {
        case ResolvesTo(target@WithApiStatus(status)) if !elementsAreInTheSameModule(ref, target)  =>
            ref.nameId.toOption.map { elementToHighlight =>
              val name = ref.refName
              holder.registerProblem(elementToHighlight, ScalaInspectionBundle.message("symbol.name.is.marked.as.status", name, status))
            }
        case _ =>
      }
    case _ =>
  }
}

object ApiStatusInspection {
  private val apiStatusAnnotations = Map(
    "org.jetbrains.annotations.ApiStatus.Internal" -> "internal",
    "org.jetbrains.annotations.ApiStatus.Experimental" -> "experimental",
    "org.jetbrains.annotations.ApiStatus.ScheduledForRemoval" -> "scheduled for removal",
  )

  object WithApiStatus {
    def unapply(element: PsiElement): Option[String] = element match {
      case Constructor(Status(status)) => Some(status)
      case Constructor.ofClass(Status(status)) => Some(status)
      case apply@ScFunction.inSynthetic(Status(status)) if apply.isApplyMethod => Some(status)
      case Status(status) => Some(status)
      case _ => None
    }
  }

  object Status {
    def unapply(element: PsiDocCommentOwner): Option[String] =
      apiStatusAnnotations.keysIterator.find(element.hasAnnotation).map(apiStatusAnnotations)
  }
}