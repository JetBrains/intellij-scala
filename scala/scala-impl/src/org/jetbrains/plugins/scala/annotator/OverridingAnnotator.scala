package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.quickfix.PullUpQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScFieldId}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TermSignature}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
  * User: Alexander Podkhalyuzin
  * Date: 30.01.12
  */

trait OverridingAnnotator {

  import lang.psi.ScalaPsiUtil._

  private def isConcreteElement(element: PsiElement): Boolean = {
    element match {
      case _: ScFunctionDefinition => true
      case f: ScFunctionDeclaration if f.isNative => true
      case _: ScFunctionDeclaration => false
      case _: ScFun => true
      case Constructor.ofClass(c) if c.isInterface => false
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

  private def isConcrete(signature: TermSignature): Boolean = {
    val element = nameContext(signature.namedElement)
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

  def checkOverrideMethods(function: ScFunction, isInSources: Boolean = false)
                          (implicit holder: AnnotationHolder): Unit = function.getParent match {
    case _: ScTemplateBody =>
      val signaturesWithSelfType = function.superSignaturesIncludingSelfType
      val signatures = function.superSignatures
      checkStructural(function, signatures, isInSources)
      checkOverrideMembers(function, function, signaturesWithSelfType, function.superSignatures, isConcrete, "Method")
    case _ =>
  }

  def checkOverrideValues(value: ScValue, isInSources: Boolean = false)
                         (implicit holder: AnnotationHolder): Unit = value.getParent match {
    case _: ScTemplateBody |
         _: ScEarlyDefinitions =>
      value.declaredElements.foreach { td =>
        val valsSignaturesWithSelfType = superValsSignatures(td, withSelfType = true)
        val valsSignatures = superValsSignatures(td)
        checkStructural(value, valsSignatures, isInSources)
        checkOverrideMembers(td, value, valsSignaturesWithSelfType, valsSignatures, isConcrete, "Value")
      }
    case _ =>
  }

  def checkOverrideVariables(variable: ScVariable, isInSources: Boolean = false)
                            (implicit holder: AnnotationHolder): Unit = variable.getParent match {
    case _: ScTemplateBody |
         _: ScEarlyDefinitions =>
      variable.declaredElements.foreach { td =>
        val valsSignaturesWithSelfType = superValsSignatures(td, withSelfType = true)
        val valsSignatures = superValsSignatures(td)
        checkStructural(variable, valsSignatures, isInSources)
        checkOverrideMembers(td, variable, valsSignaturesWithSelfType, valsSignatures, isConcrete, "Variable")
      }
    case _ =>
  }

  def checkOverrideClassParameters(parameter: ScClassParameter)
                                  (implicit holder: AnnotationHolder): Unit = {
    val supersWithSelfType = superValsSignatures(parameter, withSelfType = true)
    val supers = superValsSignatures(parameter)
    checkOverrideMembers(parameter, parameter, supersWithSelfType, supers, isConcrete, "Parameter")
  }

  def checkOverrideTypeAliases(alias: ScTypeAlias)
                              (implicit holder: AnnotationHolder): Unit = alias.getParent match {
    case _: ScTemplateBody =>
      val supersWithSelfType = superTypeMembers(alias, withSelfType = true).filter(_.isInstanceOf[ScTypeAlias])
      val supers = superTypeMembers(alias).filter(_.isInstanceOf[ScTypeAlias])
      checkOverrideMembers(alias, alias, supersWithSelfType, supers, isConcreteElement, "Type")
    case _ =>
  }

  private def checkOverrideMembers[T <: ScNamedElement, Res](member: T,
                                                             owner: ScModifierListOwner,
                                                             superSignaturesWithSelfType: Seq[Res],
                                                             superSignatures: Seq[Res],
                                                             isConcrete: Res => Boolean,
                                                             memberType: String)
                                                            (implicit holder: AnnotationHolder): Unit = {
    import lang.lexer.{ScalaModifier, ScalaTokenTypes}
    import ScalaModifier.{OVERRIDE, Override}
    import quickfix.ModifierQuickFix._

    val memberNameId = member.nameId
    if (superSignaturesWithSelfType.isEmpty) {
      if (owner.hasModifierProperty(OVERRIDE)) {
        val annotation = holder.createErrorAnnotation(
          memberNameId,
          ScalaBundle.message("member.overrides.nothing", memberType, member.name)
        )
        annotation.registerFix(new Remove(owner, memberNameId, Override))
        annotation.registerFix(new PullUpQuickFix(owner, memberNameId))
      }
    } else if (isConcreteElement(nameContext(member))) {
      var isConcretes = false
      for (signature <- superSignatures if !isConcretes && isConcrete(signature)) isConcretes = true
      if (isConcretes && !owner.hasModifierProperty(OVERRIDE)) {
        val annotation = holder.createErrorAnnotation(
          memberNameId,
          ScalaBundle.message("member.needs.override.modifier", memberType, member.name)
        )

        val maybeQuickFix: Option[Add] = member match {
          case param: ScClassParameter if param.isCaseClassVal && !(param.isVal || param.isVar) =>
            superSignaturesWithSelfType.headOption.collect {
              case signature: TermSignature => signature.namedElement
            }.flatMap { element =>
              import ScalaTokenTypes.{kVAL, kVAR}
              nameContext(element) match {
                case parameter: ScClassParameter =>
                  val keywordElementType =
                    if (parameter.isVal || (parameter.isCaseClassVal && !parameter.isVar)) kVAL
                    else kVAR
                  Some(keywordElementType)
                case _: ScValue | _: ScFunction => Some(kVAL)
                case _: ScVariable => Some(kVAR)
                case _ => None
              }
            }.map {
              new AddWithKeyword(owner, memberNameId, _)
            }
          case _ => Some(new Add(owner, memberNameId, Override))
        }
        maybeQuickFix.foreach(annotation.registerFix)
      }
      //fix for SCL-7831
      var overridesFinal = false
      for (signature <- superSignatures if !overridesFinal) {
        val e =
          signature match {
            case signature: TermSignature => signature.namedElement
            case _ => signature
          }
        e match {
          case owner1: PsiModifierListOwner if owner1.hasFinalModifier =>
            overridesFinal = true
          case _ =>
        }
      }
      if (overridesFinal) {
        holder.createErrorAnnotation(memberNameId,
          ScalaBundle.message("can.not.override.final", memberType, member.name))
      }

      def annotateVarFromVal(): Unit = {
        def addAnnotation(): Unit = {
          holder.createErrorAnnotation(memberNameId,
            ScalaBundle.message("var.cannot.override.val", member.name))
        }

        for (signature <- superSignatures) {
          signature match {
            case s: TermSignature =>
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
          holder.createErrorAnnotation(memberNameId,
            ScalaBundle.message("member.cannot.override.val", member.name))
        }

        def annotVar() = {
          holder.createErrorAnnotation(memberNameId,
            ScalaBundle.message("member.cannot.override.var", member.name))
        }

        for (signature <- superSignatures) {
          signature match {
            case s: TermSignature =>
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
        case inNameContext(_: ScVariable) =>
          annotateVarFromVal()
        case cp: ScClassParameter if cp.isVar =>
          annotateVarFromVal()
        case _ =>
      }
    }

    def effectiveParams(fun: ScFunction) = fun.effectiveParameterClauses.flatMap(_.effectiveParameters)

    def overrideTypeMatchesBase(baseType: ScType, overType: ScType, s: TermSignature, baseName: String): Boolean = {
      val actualType = if (s.name == baseName + "_=") {
        overType match {
          case ParameterizedType(des, args) if des.canonicalText == "_root_.scala.Function1" => args.head
          case _ => return true
        }
      } else overType
      val actualBase = (s.namedElement, member) match {
        case (sFun: ScFunction, mFun: ScFunction) if effectiveParams(sFun).length == effectiveParams(mFun).length &&
          s.typeParamsLength == mFun.typeParameters.length =>
          val sParams = effectiveParams(sFun)
          val mParams = effectiveParams(mFun)
          val sTypeParams = s.typeParams
          val mTypeParams = mFun.typeParameters

          val subst =
            if (sParams.size != mParams.size || sTypeParams.size != mTypeParams.size)
              s.substitutor
            else {
              val typeParamSubst = ScSubstitutor.bind(sTypeParams, mTypeParams)(TypeParameterType(_))
              val paramTypesSubst = ScSubstitutor.paramToParam(sParams, mParams)
              s.substitutor.followed(typeParamSubst).followed(paramTypesSubst)
            }
          subst(baseType)
        case _ => s.substitutor(baseType)
      }

      def allowEmptyParens(pat: ScBindingPattern): Boolean = pat.nameContext match {
        case v: ScValueOrVariable => v.typeElement.isDefined || PropertyMethods.isBeanProperty(v)
        case _ => false
      }

      actualType.conforms(actualBase) || ((actualBase, actualType, member) match {
        case (ParameterizedType(des, args), _, pat: ScBindingPattern) if des.canonicalText == "_root_.scala.Function0" &&
          allowEmptyParens(pat) => actualType.conforms(args.head)
        case (ParameterizedType(des, args), _, _: ScFunction) if des.canonicalText == "_root_.scala.Function0" =>
          actualType.conforms(args.head)
        case (aType, ParameterizedType(des, args), _: ScFunction) if des.canonicalText == "_root_.scala.Function0" =>
          aType.conforms(args.head)
        case _ => false
      })
    }

    def comparableType(named: PsiNamedElement): Option[ScType] = named match {
      case cp: ScClassParameter => cp.getRealParameterType.toOption
      case fun: ScFunction if fun.isEmptyParen || fun.isParameterless => fun.returnType.toOption
      case t: Typeable => t.`type`().toOption
      case _ => None
    }

    for {
      overridingType <- comparableType(member)
      superSig <- superSignatures.filterBy[TermSignature]
      baseType <- comparableType(superSig.namedElement)
      if !overrideTypeMatchesBase(baseType, overridingType, superSig, superSig.namedElement.name)
    } {
      holder.createErrorAnnotation(memberNameId,
        ScalaBundle.message("override.types.not.conforming", overridingType.presentableText, baseType.presentableText))
    }
  }
}
