package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{superTypeSignatures, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScFieldId}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueDeclaration, ScValueOrVariableDefinition, ScVariableDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.Signature

import scala.annotation.tailrec

abstract class OverridingTargetNameInspectionBase extends TargetNameInspectionBase {
  override protected val findProblemElement: PartialFunction[PsiElement, ProblemElement] = Function.unlift {
    case param: ScClassParameter if !param.isVar && checkAnnotation(param) =>
      findProblemElement(param, param.nameId, superValsSignatures(param))
    case value: ScValueDeclaration if value.getIdList.fieldIds.sizeIs == 1 && checkAnnotation(value) =>
      val fieldId = value.declaredElements.head
      findProblemElement(value, fieldId, superValsSignatures(fieldId))
    case variable: ScVariableDeclaration if variable.getIdList.fieldIds.sizeIs == 1 && checkAnnotation(variable) =>
      val fieldId = variable.declaredElements.head
      findProblemElement(variable, fieldId, superValsSignatures(fieldId))
    case valOrVar: ScValueOrVariableDefinition if valOrVar.isSimple && checkAnnotation(valOrVar) =>
      val binding = valOrVar.bindings.head
      findProblemElement(valOrVar, binding, superValsSignatures(binding))
    case function: ScFunction if checkAnnotation(function) =>
      findProblemElement(function, function.nameId, function.superSignaturesIncludingSelfType)
    case typeAlias: ScTypeAlias if checkAnnotation(typeAlias) =>
      findProblemElement(typeAlias, typeAlias.nameId, superTypeSignatures(typeAlias))
    case _ => None
  }

  private def findProblemElement(childElement: ScAnnotationsHolder, anchor: PsiElement, superSignatures: Seq[Signature]): Option[ProblemElement] =
    superSignatures
      .view
      .flatMap(superElementWithProblem(childElement))
      .headOption
      .map { superElement =>
        ProblemElement(anchor, createQuickFix(childElement, superElement), createDescription(superElement))
      }

  private def superElementWithProblem(childElem: ScAnnotationsHolder)
                                     (superSignature: Signature): Option[ScAnnotationsHolder] = {
    @tailrec def findProblemElem(element: PsiElement): Option[ScAnnotationsHolder] = element match {
      case superElem: ScAnnotationsHolder if checkSuperAnnotation(superElem, childElem) => Some(superElem)
      case ref: ScReferencePattern => findProblemElem(ref.nameContext)
      case fieldId: ScFieldId => findProblemElem(fieldId.nameContext)
      case _ => None
    }

    findProblemElem(superSignature.namedElement)
  }

  protected def createQuickFix(element: ScAnnotationsHolder, superElement: ScAnnotationsHolder): Option[LocalQuickFix]

  protected def createDescription(element: ScAnnotationsHolder): Option[String]

  protected def checkAnnotation(element: ScAnnotationsHolder): Boolean

  protected def checkSuperAnnotation(superElement: ScAnnotationsHolder, element: ScAnnotationsHolder): Boolean
}

class OverridingAddingTargetNameInspection extends OverridingTargetNameInspectionBase {

  import OverridingAddingTargetNameInspection._

  override protected def createDescription(element: ScAnnotationsHolder): Option[String] = Some(message)

  override protected def checkAnnotation(element: ScAnnotationsHolder): Boolean =
    hasTargetNameAnnotation(element)

  override protected def checkSuperAnnotation(superElement: ScAnnotationsHolder, element: ScAnnotationsHolder): Boolean =
    !checkAnnotation(superElement)

  override protected def createQuickFix(element: ScAnnotationsHolder, superElement: ScAnnotationsHolder): Option[LocalQuickFix] = for {
    annotation <- lastTargetNameAnnotation(element)
    listOwner <- element.asOptionOf[ScModifierListOwner]
  } yield new RemoveAnnotationQuickFix(annotation, listOwner)
}

object OverridingAddingTargetNameInspection {
  private[targetNameAnnotation] val message =
    ScalaInspectionBundle.message("override.definition.should.not.have.targetname.annotation")
}

class OverridingRemovingTargetNameInspection extends OverridingTargetNameInspectionBase {

  import OverridingRemovingTargetNameInspection._

  override protected def createDescription(element: ScAnnotationsHolder): Option[String] =
    targetNameAnnotationExternalName(element).map(message)

  override protected def checkAnnotation(element: ScAnnotationsHolder): Boolean =
    !hasTargetNameAnnotation(element)

  override protected def checkSuperAnnotation(superElement: ScAnnotationsHolder, element: ScAnnotationsHolder): Boolean =
    !checkAnnotation(superElement)

  override protected def createQuickFix(element: ScAnnotationsHolder, superElement: ScAnnotationsHolder): Option[LocalQuickFix] =
    targetNameAnnotationExternalName(superElement).map { superExtName =>
      new AddTargetNameAnnotationQuickFix(element, extName = superExtName)
    }
}

object OverridingRemovingTargetNameInspection {
  private[targetNameAnnotation] def message(superExtName: String) =
    ScalaInspectionBundle.message("override.definition.misses.targetname.annotation", superExtName)
}

class OverridingWithDifferentTargetNameInspection extends OverridingTargetNameInspectionBase {

  import OverridingWithDifferentTargetNameInspection._

  override protected def createDescription(element: ScAnnotationsHolder): Option[String] =
    targetNameAnnotationExternalName(element).map(message)

  override protected def checkAnnotation(element: ScAnnotationsHolder): Boolean =
    hasTargetNameAnnotation(element)

  override protected def checkSuperAnnotation(superElement: ScAnnotationsHolder, element: ScAnnotationsHolder): Boolean =
    (targetNameAnnotationExternalName(element), targetNameAnnotationExternalName(superElement)) match {
      case (Some(extName), Some(superExtName)) => extName != superExtName
      case _ => false
    }

  override protected def createQuickFix(element: ScAnnotationsHolder, superElement: ScAnnotationsHolder): Option[LocalQuickFix] = {
    for {
      listOwner <- element.asOptionOf[ScModifierListOwner]
      annotation <- lastTargetNameAnnotation(element)
      firstAnnotationParam <- annotation.annotationExpr.getAnnotationParameters.headOption
      superExtName <- targetNameAnnotationExternalName(superElement)
    } yield new AbstractFixOnPsiElement(ScalaInspectionBundle.message("fix.targetname.annotation"), listOwner) {
      override protected def doApplyFix(element: ScModifierListOwner)(implicit project: Project): Unit = {
        val newAnnotationParam = ScalaPsiElementFactory.createExpressionFromText(s""""$superExtName"""")
        firstAnnotationParam.replace(newAnnotationParam)
      }
    }
  }
}

object OverridingWithDifferentTargetNameInspection {
  private[targetNameAnnotation] def message(superExtName: String) =
    ScalaInspectionBundle.message("override.definition.has.different.target.name", superExtName)
}
