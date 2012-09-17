package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.{PsiModifier, PsiMethod, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.Signature
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.ScalaBundle
import com.intellij.codeInspection.ProblemHighlightType
import params.ScClassParameter
import quickfix.modifiers.{AddModifierQuickFix, RemoveModifierQuickFix}
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import org.jetbrains.plugins.scala.extensions.toPsiModifierListOwnerExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.12
 */

trait OverridingAnnotator {
  private def isConcreteElement(element: PsiElement): Boolean = {
    element match {
      case _: ScFunctionDefinition => true
      case f: ScFunctionDeclaration if f.isNative => true
      case _: ScFunctionDeclaration => false
      case _: ScFun => true
      case method: PsiMethod if !method.hasAbstractModifier && !method.isConstructor => true
      case method: PsiMethod if method.hasModifierProperty(PsiModifier.NATIVE) => true
      case _: ScPatternDefinition => true
      case _: ScVariableDefinition => true
      case _: ScClassParameter => true
      case _: ScTypeDefinition => true
      case _: ScTypeAliasDefinition => true
      case _ => false
    }
  }

  def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false
    checkOverrideMembers(method, method, method.superSignaturesIncludingSelfType, isConcrete, "Method", holder)
  }

  def checkOverrideVals(v: ScValue, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false

    v.declaredElements.foreach(td => {
      checkOverrideMembers(td, v, ScalaPsiUtil.superValsSignatures(td, withSelfType = true), isConcrete, "Value", holder)
    })
  }

  def checkOverrideVars(v: ScVariable, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false

    v.declaredElements.foreach(td => {
      checkOverrideMembers(td, v, ScalaPsiUtil.superValsSignatures(td, withSelfType = true), isConcrete, "Variable", holder)
    })
  }

  def checkOverrideClassParameters(v: ScClassParameter, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false

    checkOverrideMembers(v, v, ScalaPsiUtil.superValsSignatures(v, withSelfType = true), isConcrete, "Parameter", holder)
  }

  def checkOverrideTypes(tp: ScNamedElement with ScModifierListOwner, holder: AnnotationHolder) {
    tp match {
      case c: ScTypeDefinition =>
      case a: ScTypeAlias =>
      case _ => return
    }
    checkOverrideMembers(tp, tp, ScalaPsiUtil.superTypeMembers(tp, withSelfType = true), isConcreteElement, "Type", holder)
  }
  private def checkOverrideMembers[T <: ScNamedElement, Res](member: T,
                                                             owner: ScModifierListOwner,
                                                             superSignatures: Seq[Res],
                                                             isConcrete: Res => Boolean,
                                                             memberType: String,
                                                             holder: AnnotationHolder) {
    if (superSignatures.length == 0) {
      if (owner.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(member.nameId,
          ScalaBundle.message("member.overrides.nothing", memberType, member.name))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new RemoveModifierQuickFix(owner, "override"))
      }
    } else if (isConcreteElement(ScalaPsiUtil.nameContext(member))) {
      var isConcretes = false
      for (signature <- superSignatures if !isConcretes && isConcrete(signature)) isConcretes = true
      if (isConcretes && !owner.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(member.nameId,
          ScalaBundle.message("member.needs.override.modifier", memberType, member.name))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new AddModifierQuickFix(owner, "override"))
      }
    }
  }
}
