package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.superValsSignatures
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

class ScalaOverrideModifierMissingInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    def noOverrideModifier(member: ScModifierListOwner): Boolean = !member.hasModifierProperty("override")
    val hasSuper: PsiNamedElement => Boolean = superValsSignatures(_, withSelfType = true).nonEmpty

    lazy val problemDescriptor = super.problemDescriptor(element, createQuickFix(element), descriptionTemplate, ProblemHighlightType.INFORMATION)

    element match {
      case method: ScFunction if noOverrideModifier(method) && method.superMethod.isDefined => problemDescriptor
      case param: ScParameter if noOverrideModifier(param) && hasSuper(param) => problemDescriptor
      case expr: ScValueOrVariable if noOverrideModifier(expr) && expr.declaredElements.exists(hasSuper) => problemDescriptor
      case _ => None
    }
  }

  def createQuickFix(element: PsiElement): Option[LocalQuickFix] = {
    Some(new AbstractFixOnPsiElement(InspectionBundle.message("method.signature.override.modifier.missing"), element) {
      override protected def doApplyFix(element: PsiElement)(implicit project: Project): Unit = {
        element match {
          case mOwner: ScModifierListOwner => mOwner.getModifierList.setModifierProperty("override", true)
        }
      }
    })
  }

}
