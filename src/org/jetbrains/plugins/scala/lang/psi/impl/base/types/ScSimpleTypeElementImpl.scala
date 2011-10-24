package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.base.types._
import psi.ScalaPsiElementImpl
import lexer.ScalaTokenTypes
import psi.types._
import nonvalue.{ScTypePolymorphicType, TypeParameter, Parameter}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import result.{Failure, TypeResult, Success, TypingContext}
import scala.None
import api.statements._
import api.toplevel.ScTypeParametersOwner
import params.ScTypeParam
import psi.types.Compatibility.Expression
import collection.immutable.HashMap
import api.expr.{ScSuperReference, ScThisReference, ScUnderScoreSectionUtil}
import lang.resolve.ScalaResolveResult
import api.base._

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {
  override def toString: String = "SimpleTypeElement"

  def singleton = getNode.findChildByType(ScalaTokenTypes.kTYPE) != null

  def findConstructor: Option[ScConstructor] = {
    getContext match {
      case constr: ScConstructor => Some(constr)
      case param: ScParameterizedTypeElement => {
        param.getContext match {
          case constr: ScConstructor => Some(constr)
          case _ => None
        }
      }
      case _ => None
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val lift: (ScType) => Success[ScType] = Success(_, Some(this))

    def parameterize(tp: ScType, clazz: PsiClass, subst: ScSubstitutor): ScType = {
      if (clazz.getTypeParameters.length == 0) {
        tp
      } else {
        ScParameterizedType(tp, clazz.getTypeParameters.map(ptp => ptp match {
          case tp: ScTypeParam => new ScTypeParameterType(tp, subst)
          case _ => new ScTypeParameterType(ptp, subst)
        }))
      }
    }

    def typeForConstructor(ref: ScStableCodeReferenceElement, constr: PsiMethod,
                           _subst: ScSubstitutor, parentElement: PsiNamedElement): ScType = {
      val clazz = constr.getContainingClass
      val (constrTypParameters: Seq[ScTypeParam], constrSubst: ScSubstitutor) = parentElement match {
        case ta: ScTypeAliasDefinition => (Seq.empty, ScSubstitutor.empty)
        case s: ScTypeParametersOwner if s.typeParameters.length > 0 =>
          constr match {
            case method: ScMethodLike =>
              val params = method.getConstructorTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
              val subst = new ScSubstitutor(s.typeParameters.zip(params).map {
                case (tpClass: ScTypeParam, tpConstr: ScTypeParam) => {
                  ((tpClass.getName, ScalaPsiUtil.getPsiElementId(tpClass)),
                    new ScTypeParameterType(tpConstr, ScSubstitutor.empty))
                }
              }.toMap, Map.empty, None)
              (params, subst)
            case _ => (Seq.empty, ScSubstitutor.empty)
          }
        case _ => (Seq.empty, ScSubstitutor.empty)
      }
      val subst = _subst followed constrSubst
      val tp = parentElement match {
        case ta: ScTypeAliasDefinition =>
          ta.aliasedType.getOrElse(return Nothing)
        case _ =>
          parameterize(ScSimpleTypeElementImpl.calculateReferenceType(ref, false).
            getOrElse(return Nothing), clazz, subst)
      }
      val res = subst.subst(tp)
      val typeParameters: Seq[TypeParameter] = parentElement match {
        case tp: ScTypeParametersOwner if constrTypParameters.length > 0 =>
          constrTypParameters.map(tp => new TypeParameter(tp.name,
                tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
        case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 => {
          tp.typeParameters.map(tp => new TypeParameter(tp.name,
                tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
        }
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.length > 0 => {
          ptp.getTypeParameters.toSeq.map(ptp => new TypeParameter(ptp.getName,
            Nothing, Any, ptp)) //todo: add lower and upper bound
        }
        case _ => return res
      }

      getContext match {
        case p: ScParameterizedTypeElement => {
          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ (zipped.map{case (arg, typeParam) =>
            (
              (typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)),
              arg.getType(TypingContext.empty).getOrAny
              )}),
            Map.empty, None)
          return appSubst.subst(res)
        }
        case _ =>
      }


      val params: Seq[Seq[Parameter]] = constr match {
        case fun: ScFunction =>
          fun.paramClauses.clauses.map(_.parameters.map(p => new Parameter(p.name,
            subst.subst(p.getType(TypingContext.empty).getOrAny), p.isDefaultParam,
            p.isRepeatedParameter, p.isCallByNameParameter)))
        case f: ScPrimaryConstructor =>
          f.parameterList.clauses.map(_.parameters.map(p => new Parameter(p.name,
            subst.subst(p.getType(TypingContext.empty).getOrAny), p.isDefaultParam,
            p.isRepeatedParameter, p.isCallByNameParameter)))
        case m: PsiMethod =>
          Seq(m.getParameterList.getParameters.toSeq.map(p => new Parameter("",
            ScType.create(p.getType, getProject, getResolveScope, paramTopLevel = true), false, p.isVarArgs, false)))
      }

      findConstructor match {
        case Some(c) => {
          var nonValueType = ScTypePolymorphicType(res, typeParameters)
          var i = 0
          while (i < params.length - 1 && i < c.arguments.length) {
            nonValueType = ScalaPsiUtil.localTypeInference(nonValueType.internalType, params(i),
              c.arguments(i).exprs.map(new Expression(_)), nonValueType.typeParameters)
            i += 1
          }
          //todo: last implicit clause + right order
          //todo: add check according to expected type and without it
          c.expectedType match {
            case Some(expected) => {
              def updateRes(expected: ScType) {
                nonValueType = ScalaPsiUtil.localTypeInference(nonValueType.internalType,
                  Seq(new Parameter("", expected, false, false, false)),
                    Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(nonValueType.typeParameters).subst(res.inferValueType))),
                  nonValueType.typeParameters, shouldUndefineParameters = false) //here should work in different way:
              }
              val fromUnderscore = c.newTemplate match {
                case Some(n) => ScUnderScoreSectionUtil.underscores(n).length != 0
                case None => false
              }
              if (!fromUnderscore) {
                updateRes(expected)
              } else {
                expected match {
                  case ScFunctionType(retType, _) => updateRes(retType)
                  case _ => //do not update res, we haven't expected type
                }
              }
            }
            case _ =>
          }

          //last clause after expected types
          if (i < params.length && i < c.arguments.length) {
            nonValueType = ScalaPsiUtil.localTypeInference(nonValueType.internalType, params(i),
              c.arguments(i).exprs.map(new Expression(_)), nonValueType.typeParameters)
          }


          val pts = new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp =>
            ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
                  if (tp.upperType.equiv(Any)) tp.lowerType else if (tp.lowerType.equiv(Nothing)) tp.upperType
                  else tp.lowerType))),
            Map.empty, None)
          pts.subst(nonValueType.internalType) //todo: simple type element should have non value type
        }
        case None => res
      }
    }

    reference match {
      case Some(ref) =>
        def updateForParameterized(subst: ScSubstitutor, elem: PsiNamedElement,
                                    p: ScParameterizedTypeElement): ScType = {
          val tp = elem match {
            case ta: ScTypeAliasDefinition =>
              ta.aliasedType.getOrElse(return Nothing)
            case clazz: PsiClass =>
              parameterize(ScSimpleTypeElementImpl.calculateReferenceType(ref, false).
                getOrElse(return Nothing), clazz, subst)
          }
          val res = subst.subst(tp)
          val typeParameters: Seq[TypeParameter] = elem match {
            case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 => {
              tp.typeParameters.map(tp => new TypeParameter(tp.name,
                    tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
            }
            case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.length > 0 => {
              ptp.getTypeParameters.toSeq.map(ptp => new TypeParameter(ptp.getName,
                Nothing, Any, ptp)) //todo: add lower and upper bound
            }
            case _ => return res
          }

          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ (zipped.map{case (arg, typeParam) =>
            ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)),
              arg.getType(TypingContext.empty).getOrAny
              )}),
            Map.empty, None)
          appSubst.subst(res)
        }
        val constrRef = ref.isConstructorReference
        ref.resolveNoConstructor match {
          case Array(ScalaResolveResult(tp: PsiTypeParameter, _)) =>
            lift(ScalaPsiManager.typeVariable(tp))
          case Array(ScalaResolveResult(synth: ScSyntheticClass, _)) =>
            lift(synth.t)
          case Array(ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
            if constrRef && to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.length == 0 || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
            getContext match {
              case p: ScParameterizedTypeElement =>
                Success(updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p), Some(this))
              case _ =>
                ScSimpleTypeElementImpl.calculateReferenceType(ref, false)
            }
          case Array(ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
            if constrRef && to.isInstanceOf[PsiNamedElement] &&
              (to.getTypeParameters.length == 0 || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
            getContext match {
              case p: ScParameterizedTypeElement =>
                Success(updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p), Some(this))
              case _ =>
                ScSimpleTypeElementImpl.calculateReferenceType(ref, false)
            }
          case _ => //resolve constructor with local type inference
            ref.bind() match {
              case Some(r@ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) => {
                Success(typeForConstructor(ref, method, subst, r.getActualElement), Some(this))
              }
              case _ => ScSimpleTypeElementImpl.calculateReferenceType(ref, false)
            }
        }
      case None => ScSimpleTypeElementImpl.calculateReferenceType(pathElement, false)
    }
  }
}

object ScSimpleTypeElementImpl {
  def calculateReferenceType(path: ScPathElement, shapesOnly: Boolean): TypeResult[ScType] = {
    path match {
      case ref: ScStableCodeReferenceElement => calculateReferenceType(ref, shapesOnly)
      case thisRef: ScThisReference => {
        thisRef.refTemplate match {
          case Some(template) => {
            Success(ScThisType(template), Some(path))
          }
          case _ => Failure("Cannot find template for this reference", Some(thisRef))
        }
      }
      case superRef: ScSuperReference => {
        val template = superRef.drvTemplate.getOrElse(
            return Failure("Cannot find enclosing container", Some(superRef))
          )
        Success(ScThisType(template), Some(path))
      }
    }
  }

  def calculateReferenceType(ref: ScStableCodeReferenceElement, shapesOnly: Boolean): TypeResult[ScType] = {
    val (resolvedElement, subst, fromType) = (if (!shapesOnly) {
      if (ref.isConstructorReference) {
        ref.resolveNoConstructor match {
          case Array(r@ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.length == 0 || ref.getContext.isInstanceOf[ScParameterizedTypeElement]) => Some(r)
          case Array(r@ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.getTypeParameters.length == 0 || ref.getContext.isInstanceOf[ScParameterizedTypeElement]) => Some(r)
          case _ => ref.bind()
        }
      } else ref.bind()
    } else {
      ref.shapeResolve match {
        case Array(r: ScalaResolveResult) => Some(r)
        case _ => None
      }
    }) match {
      case Some(r@ScalaResolveResult(n: PsiMethod, resolveSubstitutor)) if n.isConstructor =>
        (n.getContainingClass, resolveSubstitutor, r.fromType)
      case Some(r@ScalaResolveResult(n: PsiNamedElement, subst: ScSubstitutor)) => (n, subst, r.fromType)
      case _ => return Failure("Cannot resolve reference", Some(ref))
    }
    ref.qualifier match {
      case Some(qual) => {
        qual.resolve() match {
          case pack: PsiPackage => {
            Success(ScType.designator(resolvedElement), Some(ref))
          }
          case _ => {
            calculateReferenceType(qual, shapesOnly) match {
              case failure: Failure => failure
              case Success(tp, _) => {
                Success(ScProjectionType(tp, resolvedElement, subst), Some(ref))
              }
            }
          }
        }
      }
      case None => {
        ref.pathQualifier match {
          case Some(thisRef: ScThisReference) => {
            thisRef.refTemplate match {
              case Some(template) => {
                Success(ScProjectionType(ScThisType(template), resolvedElement, subst), Some(ref))
              }
              case _ => Failure("Cannot find template for this reference", Some(thisRef))
            }
          }
          case Some(superRef: ScSuperReference) => {
            val template = superRef.drvTemplate match {
              case Some(x) => x
              case None => return Failure("Cannot find enclosing container", Some(superRef))
            }
            Success(ScProjectionType(ScThisType(template), resolvedElement, subst), Some(ref))
          }
          case None => {
            if (fromType == None) return Success(ScType.designator(resolvedElement), Some(ref))
            Success(ScProjectionType(fromType.get, resolvedElement, subst), Some(ref))
          }
        }
      }
    }
  }
}