package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isConcreteTermSignature, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.TermSignature

class OverrideAbstractMemberInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case param: ScClassParameter if isApplicable(param) =>
      holder.registerProblem(param.nameId, getDisplayName, createQuickFix(param))

    case v: ScValue if !hasOverride(v) && !PropertyMethods.isBeanProperty(v) =>
      val firstOverrideElement =
        v.declaredElements.find(elem => isApplicable(superValsSignatures(elem)))

      firstOverrideElement.foreach(elem => holder.registerProblem(elem.nameId, getDisplayName, createQuickFix(v)))

    case function: ScFunction if isApplicable(function) =>
      holder.registerProblem(function.nameId, getDisplayName, createQuickFix(function))

    case _ =>
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

  private def createQuickFix(element: ScModifierListOwner): LocalQuickFix =
    new AbstractFixOnPsiElement[ScModifierListOwner](
      ScalaInspectionBundle.message("add.override.modifier.quickfix"),
      element
    ) {
      override protected def doApplyFix(modifierListOwner: ScModifierListOwner)(implicit project: Project): Unit = {
        val modifierList = modifierListOwner.getModifierList

        modifierList.setModifierProperty(ScalaModifier.OVERRIDE, true)

        modifierListOwner match {
          case classParam: ScClassParameter if !classParam.isVal =>
            val valKeyword = ScalaPsiElementFactory.createDeclarationFromText(
              "val x",
              modifierListOwner.getParent,
              modifierListOwner
            ).findFirstChildByType(ScalaTokenTypes.kVAL).get
            modifierListOwner.addAfter(valKeyword, modifierList)
          case _ =>
        }
      }
    }
}
