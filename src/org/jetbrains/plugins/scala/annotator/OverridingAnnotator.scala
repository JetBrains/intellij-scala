package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.psi.{PsiElement, PsiMethod, PsiModifier, PsiModifierListOwner}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.modifiers.{AddModifierQuickFix, AddModifierWithValOrVarQuickFix, RemoveModifierQuickFix}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.types.Signature

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
      case method: PsiMethod if method.getContainingClass != null && method.getContainingClass.isInterface => false
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

  private def isConcrete(signature: Signature): Boolean = {
    val element = ScalaPsiUtil.nameContext(signature.namedElement)
    isConcreteElement(element)
  }

  def checkStructural(element: PsiElement, supers: Seq[Any], isInSources: Boolean): Unit = {
    if (!isInSources) return
    element.getParent match {
      case ref: ScRefinement =>
        if (supers.isEmpty) UsageTrigger.trigger("scala.structural.type")
      case _ =>
    }
  }

  def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder, isInSources: Boolean) {
    val signaturesWithSelfType: Seq[Signature] = method.superSignaturesIncludingSelfType
    val signatures: Seq[Signature] = method.superSignatures
    checkStructural(method, signatures, isInSources)
    checkOverrideMembers(method, method, signaturesWithSelfType, signatures, isConcrete, "Method", holder)
  }

  def checkOverrideVals(v: ScValue, holder: AnnotationHolder, isInSources: Boolean) {
    v.declaredElements.foreach(td => {
      val valsSignaturesWithSelfType: Seq[Signature] = ScalaPsiUtil.superValsSignatures(td, withSelfType = true)
      val valsSignatures: Seq[Signature] = ScalaPsiUtil.superValsSignatures(td, withSelfType = false)
      checkStructural(v, valsSignatures, isInSources)
      checkOverrideMembers(td, v, valsSignaturesWithSelfType, valsSignatures, isConcrete, "Value", holder)
    })
  }

  def checkOverrideVars(v: ScVariable, holder: AnnotationHolder, isInSources: Boolean) {
    v.declaredElements.foreach(td => {
      val valsSignaturesWithSelfType: Seq[Signature] = ScalaPsiUtil.superValsSignatures(td, withSelfType = true)
      val valsSignatures: Seq[Signature] = ScalaPsiUtil.superValsSignatures(td, withSelfType = false)
      checkStructural(v, valsSignatures, isInSources)
      checkOverrideMembers(td, v, valsSignaturesWithSelfType, valsSignatures, isConcrete, "Variable", holder)
    })
  }

  def checkOverrideClassParameters(v: ScClassParameter, holder: AnnotationHolder) {
    val supersWithSelfType = ScalaPsiUtil.superValsSignatures(v, withSelfType = true)
    val supers = ScalaPsiUtil.superValsSignatures(v, withSelfType = false)
    checkOverrideMembers(v, v, supersWithSelfType, supers, isConcrete, "Parameter", holder)
  }

  def checkOverrideTypes(tp: ScNamedElement with ScModifierListOwner, holder: AnnotationHolder) {
    tp match {
      case c: ScTypeDefinition => return
      case a: ScTypeAlias =>
      case _ => return
    }
    val supersWithSelfType = ScalaPsiUtil.superTypeMembers(tp, withSelfType = true).filter(_.isInstanceOf[ScTypeAlias])
    val supers = ScalaPsiUtil.superTypeMembers(tp, withSelfType = false).filter(_.isInstanceOf[ScTypeAlias])
    checkOverrideMembers(tp, tp, supersWithSelfType, supers, isConcreteElement, "Type", holder)
  }
  private def checkOverrideMembers[T <: ScNamedElement, Res](member: T,
                                                             owner: ScModifierListOwner,
                                                             superSignaturesWithSelfType: Seq[Res],
                                                             superSignatures: Seq[Res],
                                                             isConcrete: Res => Boolean,
                                                             memberType: String,
                                                             holder: AnnotationHolder) {
    if (superSignaturesWithSelfType.isEmpty) {
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

        member match {
          case param: ScClassParameter if param.isCaseClassVal && !param.isVal && !param.isVar => fixForCaseClassParameter()
          case _ => annotation.registerFix(new AddModifierQuickFix(owner, "override"))
        }

        def fixForCaseClassParameter() {
          superSignaturesWithSelfType.head match {
            case sign: Signature =>
              ScalaPsiUtil.nameContext(sign.namedElement) match {
                case p: ScClassParameter if p.isVal || (p.isCaseClassVal && !p.isVar) =>
                  annotation.registerFix(new AddModifierWithValOrVarQuickFix(owner, "override", addVal = true))
                case _: ScClassParameter =>
                  annotation.registerFix(new AddModifierWithValOrVarQuickFix(owner, "override", addVal = false))
                case _: ScValue | _: ScFunction =>
                  annotation.registerFix(new AddModifierWithValOrVarQuickFix(owner, "override", addVal = true))
                case _: ScVariable =>
                  annotation.registerFix(new AddModifierWithValOrVarQuickFix(owner, "override", addVal = false))
                case _ =>
              }
            case _ =>
          }
        }
      }
      //fix for SCL-7831
      var overridesFinal = false
      for (signature <- superSignatures if !overridesFinal) {
        val e =
          signature match {
            case signature: Signature => signature.namedElement
            case _ => signature
          }
        e match {
          case owner1: PsiModifierListOwner if owner1.hasFinalModifier =>
            overridesFinal = true
          case _ =>
        }
      }
      if (overridesFinal) {
        val annotation: Annotation = holder.createErrorAnnotation(member.nameId,
          ScalaBundle.message("can.not.override.final", memberType, member.name))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
      member match {
        case f: ScFunctionDefinition =>
          def annotVal() = {
            val annotation = holder.createErrorAnnotation(member.nameId,
              ScalaBundle.message("member.cannot.override.val", member.name))
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          }
          def annotVar() = {
            val annotation = holder.createErrorAnnotation(member.nameId,
              ScalaBundle.message("member.cannot.override.var", member.name))
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          }
          for (signature <- superSignatures) {
            signature match {
              case s:Signature =>
                s.namedElement match {
                  case rp: ScBindingPattern if rp.isVal => annotVal()
                  case rp: ScBindingPattern if rp.isVar => annotVar()
                  case cp: ScClassParameter if cp.isVal => annotVal()
                  case cp: ScClassParameter if cp.isVar => annotVar()
                  case _ =>
                }
              case _ =>
            }
          }
        case _ =>
      }
    }

  }
}
