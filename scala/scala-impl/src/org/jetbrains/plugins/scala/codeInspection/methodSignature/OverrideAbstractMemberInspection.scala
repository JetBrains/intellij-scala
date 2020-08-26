package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.TermSignature

class OverrideAbstractMemberInspection extends AbstractRegisteredInspection {
  import lang.psi.ScalaPsiUtil._

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case param: ScClassParameter if isApplicable(param) =>
        val quickfix = createQuickFix(param)
        super.problemDescriptor(param.nameId, quickfix, descriptionTemplate, highlightType)

      case v: ScValue if !hasOverride(v) && !PropertyMethods.isBeanProperty(v) =>
        val possibleOverrideElements =
          v.declaredElements.withFilter(elem => isApplicable(superValsSignatures(elem)))

        possibleOverrideElements.flatMap { elem =>
          val quickfix = createQuickFix(v)
          super.problemDescriptor(elem.nameId, quickfix, descriptionTemplate, highlightType)
        }.headOption

      case function: ScFunction if isApplicable(function) =>
        val quickfix = createQuickFix(function)
        super.problemDescriptor(function.nameId, quickfix, descriptionTemplate, highlightType)

      case _ =>
        None
    }
  }

  private def hasOverride(element: ScModifierListOwner): Boolean =
    element.hasModifierProperty(ScalaModifier.OVERRIDE)

  private def isApplicable(param: ScClassParameter): Boolean = {
    !param.isVar && !hasOverride(param) &&
      !PropertyMethods.isBeanProperty(param) && isApplicable(superValsSignatures(param))
  }

  private def isApplicable(superSignatures: Iterable[TermSignature]): Boolean =
    superSignatures.nonEmpty && superSignatures.forall(!isConcreteTermSignature(_))

  private def isApplicable(function: ScFunction): Boolean =
    !hasOverride(function) && isApplicable(function.superSignaturesIncludingSelfType)

  private def createQuickFix(modifierListOwner: ScModifierListOwner): Option[LocalQuickFix] = {
    val desc = ScalaInspectionBundle.message("add.override.modifier.quickfix")
    Some(new AbstractFixOnPsiElement[ScModifierListOwner](desc, modifierListOwner) {
      override protected def doApplyFix(element: ScModifierListOwner)(implicit project: Project): Unit = {
        val modifierList = modifierListOwner.getModifierList

        modifierList.setModifierProperty(ScalaModifier.OVERRIDE, true)

        modifierListOwner match {
          case classParam: ScClassParameter if !classParam.isVal =>
            val valKeyword = ScalaPsiElementFactory.createDeclarationFromText(
              "val x",
              modifierListOwner.getParent,
              modifierListOwner
            ).findFirstChildByType(ScalaTokenTypes.kVAL)
            modifierListOwner.addAfter(valKeyword, modifierList)
          case _ =>
        }
      }
    })
  }
}
