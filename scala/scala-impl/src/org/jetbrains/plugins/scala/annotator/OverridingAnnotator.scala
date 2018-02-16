package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.psi.{PsiElement, PsiMethod, PsiModifier, PsiModifierListOwner}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.modifiers.{AddModifierQuickFix, AddModifierWithValOrVarQuickFix, RemoveModifierQuickFix}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{AfterUpdate, ScSubstitutor}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, Signature}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

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
      case _: ScRefinement =>
        Stats.trigger(supers.isEmpty, FeatureKey.structuralType)
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
      case _: ScTypeDefinition => return
      case _: ScTypeAlias =>
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
        annotation.registerFix(new RemoveModifierQuickFix(owner, "override"))
      }
    } else if (isConcreteElement(ScalaPsiUtil.nameContext(member))) {
      var isConcretes = false
      for (signature <- superSignatures if !isConcretes && isConcrete(signature)) isConcretes = true
      if (isConcretes && !owner.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(member.nameId,
          ScalaBundle.message("member.needs.override.modifier", memberType, member.name))

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
        holder.createErrorAnnotation(member.nameId,
          ScalaBundle.message("can.not.override.final", memberType, member.name))
      }
      def annotateVarFromVal() = {
        def addAnnotation(): Unit = {
          holder.createErrorAnnotation(member.nameId,
            ScalaBundle.message("var.cannot.override.val", member.name))
        }
        for (signature <- superSignatures) {
          signature match {
            case s:Signature =>
              s.namedElement match {
                case f: ScFieldId if f.isVal => addAnnotation()
                case rp: ScBindingPattern if rp.isVal => addAnnotation()
                case cp: ScClassParameter if cp.isVal => addAnnotation()
                case _ =>
              }
            case _ =>
          }
        }
      }
      def annotateFunFromValOrVar(): Unit = {
        def annotVal() = {
          holder.createErrorAnnotation(member.nameId,
            ScalaBundle.message("member.cannot.override.val", member.name))
        }
        def annotVar() = {
          holder.createErrorAnnotation(member.nameId,
            ScalaBundle.message("member.cannot.override.var", member.name))
        }
        for (signature <- superSignatures) {
          signature match {
            case s: Signature =>
              s.namedElement match {
                case rp: ScBindingPattern if rp.isVal => annotVal()
                case rp: ScBindingPattern if rp.isVar => annotVar()
                case cp: ScClassParameter if cp.isVal => annotVal()
                case cp: ScClassParameter if cp.isVar => annotVar()
                case f: ScFieldId if f.isVal => annotVal()
                case _ =>
              }
            case _ =>
          }
        }
      }
      member match {
        case _: ScFunctionDefinition =>
          annotateFunFromValOrVar()
        case ScalaPsiUtil.inNameContext(_: ScVariable) =>
          annotateVarFromVal()
        case cp: ScClassParameter if cp.isVar =>
          annotateVarFromVal()
        case _ =>
      }
    }

    def effectiveParams(fun: ScFunction) = fun.effectiveParameterClauses.flatMap(_.effectiveParameters)

    def overrideTypeMatchesBase(baseType: ScType, overType: ScType, s: Signature, baseName: String): Boolean = {
      val actualType = if (s.name == baseName + "_=") {
        overType match {
          case ScParameterizedType(des, args) if des.canonicalText == "_root_.scala.Function1" => args.head
          case _ => return true
        }
      } else overType
      val actualBase = (s.namedElement, member) match {
        case (sFun: ScFunction, mFun: ScFunction) if effectiveParams(sFun).length == effectiveParams(mFun).length &&
          s.typeParams.length == mFun.typeParameters.length =>
          val sParams = effectiveParams(sFun)
          val mParams = effectiveParams(mFun)
          val sTypeParams = s.typeParams
          val mTypeParams = mFun.typeParameters

          val subst =
            if (sParams.size != mParams.size || sTypeParams.size != mTypeParams.size)
              s.substitutor
            else {
              val typeParamSubst = ScSubstitutor.bind(sTypeParams, mTypeParams)(TypeParameterType(_))
              val oldParamToNewTypeMap = sParams.map(p => Parameter(p)).zip(mParams.map(ScDesignatorType(_))).toMap
              val paramTypesSubst = ScSubstitutor(() => oldParamToNewTypeMap)
              s.substitutor.followed(typeParamSubst).followed(paramTypesSubst)
            }
          subst.subst(baseType)
        case _ => s.substitutor.subst(baseType)
      }
      def allowEmptyParens(pat: ScBindingPattern): Boolean = pat.nameContext match {
        case v: ScValueOrVariable => v.typeElement.isDefined || ScalaPsiUtil.isBeanProperty(v)
        case _ => false
      }
      actualType.conforms(actualBase) || ((actualBase, actualType, member) match {
        case (ScParameterizedType(des, args), _, pat: ScBindingPattern) if des.canonicalText == "_root_.scala.Function0" &&
          allowEmptyParens(pat) => actualType.conforms(args.head)
        case (ScParameterizedType(des, args), _, _: ScFunction) if des.canonicalText == "_root_.scala.Function0" =>
          actualType.conforms(args.head)
        case (aType, ScParameterizedType(des, args), _: ScFunction) if des.canonicalText == "_root_.scala.Function0" =>
          aType.conforms(args.head)
        case _ => false
      })
    }

    def compareWithSignatures(overType: ScType): Unit = {
      superSignatures.foreach {
        case s: Signature =>
          s.namedElement match {
            case tp: Typeable =>
              tp.`type`().foreach { baseType =>
                if (!overrideTypeMatchesBase(baseType, overType, s, tp.name))
                  holder.createErrorAnnotation(member.nameId,
                    ScalaBundle.message("override.types.not.conforming", overType.presentableText, baseType.presentableText))
              }
            case _ =>
          }
        case _ =>
      }
    }

    if (superSignatures.nonEmpty) member match {
      case tMember: Typeable => tMember.`type`().map(compareWithSignatures)
      case _ =>
    }
  }
}
