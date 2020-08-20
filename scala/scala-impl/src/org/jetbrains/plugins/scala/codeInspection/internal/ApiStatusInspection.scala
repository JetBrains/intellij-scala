package org.jetbrains.plugins.scala
package codeInspection
package internal

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.{PsiDocCommentOwner, PsiElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.internal.ApiStatusInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

class ApiStatusInspection extends AbstractRegisteredInspection {
  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           @Nls descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    val elemFile = element.containingFile
    element match {
      case fun: ScFunction =>
        fun.superMethod.collectFirst {
          case Status(status) && sup if !elemFile.exists(sup.containingFile.contains) =>
            val name = fun.name
            manager.createProblemDescriptor(fun.nameId, ScalaInspectionBundle.message("super.method.name.is.marked.as.status", name, status), isOnTheFly, maybeQuickFix.toArray, highlightType)
        }
      case (ref: ScReference) && ResolvesTo(target@WithApiStatus(status)) if !elemFile.exists(target.containingFile.contains)  =>
        ref.nameId.toOption.map { elementToHighlight =>
            val name = ref.refName
            manager.createProblemDescriptor(elementToHighlight, ScalaInspectionBundle.message("symbol.name.is.marked.as.status", name, status), isOnTheFly, maybeQuickFix.toArray, highlightType)
        }
      case _ => None
    }
  }
}

object ApiStatusInspection {
  val apiStatusAnnotations = Map(
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