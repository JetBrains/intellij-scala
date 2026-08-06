package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScEndImpl
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

class NoTargetNameAnnotationForOperatorLikeDefinitionInspection extends TargetNameInspectionBase {

  import NoTargetNameAnnotationForOperatorLikeDefinitionInspection._

  override protected def findProblemElement: PartialFunction[PsiElement, ProblemElement] = {
    case element@TargetNameCandidate.WithoutAnnotation(annotationHolder) =>
      val maybeQuickFix = annotationHolder match {
        case enumCases: ScEnumCases if enumCases.declaredElements.sizeIs > 1 =>
          // TODO: maybe suggest splitting cases and adding an annotation to the case with `element` nameId
          None
        case _ =>
          Some(new AddTargetNameAnnotationQuickFix(annotationHolder))
      }

      ProblemElement(element, maybeQuickFix, Some(message))
  }
}

object NoTargetNameAnnotationForOperatorLikeDefinitionInspection {
  private[targetNameAnnotation] val message =
    ScalaInspectionBundle.message("definition.with.operator.name.should.have.targetname.annotation")

  private val SetterSuffix = "_="
  private val SyntheticSetterName = "_$eq"

  private def isOperatorName(name: String): Boolean = name.exists(ScalaNamesUtil.isOpCharacter)

  private def isSetterName(name: String): Boolean = name.endsWith(SetterSuffix) || name.endsWith(SyntheticSetterName)

  private def isConstructor(element: ScalaPsiElement): Boolean = element match {
    case method: ScMethodLike => method.isConstructor
    case _ => false
  }

  private def isSynthetic(element: ScalaPsiElement): Boolean = element match {
    case member: ScMember => member.isSynthetic
    case _ => false
  }

  private def isEndMarkerTarget(element: ScalaPsiElement): Boolean = ScEndImpl.Target.unapply(element).isDefined

  object TargetNameCandidate {
    private def accepts(element: ScalaPsiElement, name: String): Boolean = isOperatorName(name) &&
      !isSetterName(name) &&
      !isConstructor(element) &&
      !isSynthetic(element) &&
      !isEndMarkerTarget(element)

    private def accepts(maybeNameElement: PsiElement, element: ScAnnotationsHolder with ScNamedElement): Boolean =
      element.nameId == maybeNameElement && accepts(element, element.name)

    private def accepts(maybeNameElement: PsiElement, element: ScValueOrVariable): Boolean = element match {
      case valOrVal: ScValueOrVariableDefinition if valOrVal.pList == maybeNameElement && valOrVal.isSimple =>
        accepts(valOrVal, valOrVal.bindings.head.name)
      case value: ScValueDeclaration if value.getIdList == maybeNameElement && value.getIdList.fieldIds.sizeIs == 1 =>
        accepts(value, value.getIdList.fieldIds.head.name)
      case variable: ScVariableDeclaration if variable.getIdList == maybeNameElement && variable.getIdList.fieldIds.sizeIs == 1 =>
        accepts(variable, variable.getIdList.fieldIds.head.name)
      case _ => false
    }

    def unapply(element: PsiElement): Option[ScModifierListOwner] = element.getParent.asOptionOf[ScModifierListOwner].collect {
      case (enumCase: ScEnumCase) & Parent(enumCases: ScEnumCases) if accepts(element, enumCase) => enumCases
      case td: ScTypeDefinition if accepts(element, td) => td
      case fn: ScFunction if accepts(element, fn) => fn
      case valOrVal: ScValueOrVariable if accepts(element, valOrVal) => valOrVal
      case param: ScClassParameter if accepts(element, param) => param
      case typeAlias: ScTypeAlias if accepts(element, typeAlias) => typeAlias
    }

    object WithoutAnnotation {
      def unapply(element: PsiElement): Option[ScModifierListOwner] =
        TargetNameCandidate.unapply(element).filterNot(hasTargetNameAnnotation)
    }
  }
}
