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
import lang.resolve.ScalaResolveResult
import api.statements._
import api.toplevel.typedef.ScObject
import api.toplevel.ScTypeParametersOwner
import params.ScTypeParam
import api.base.{ScPrimaryConstructor, ScConstructor}
import psi.types.Compatibility.Expression
import collection.immutable.HashMap
import api.expr.ScUnderScoreSectionUtil

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {
  override def toString: String = "SimpleTypeElement"

  def singleton = getNode.findChildByType(ScalaTokenTypes.kTYPE) != null

  def findConsturctor: Option[ScConstructor] = {
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

    def clazzType(clazz: PsiClass, subst: ScSubstitutor): ScType = {
      if (clazz.getTypeParameters.length == 0) {
        ScDesignatorType(clazz)
      } else {
        ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(ptp => ptp match {
          case tp: ScTypeParam => new ScTypeParameterType(tp, subst)
          case _ => new ScTypeParameterType(ptp, subst)
        }))
      }
    }

    def typeForConstructor(constr: PsiMethod, subst: ScSubstitutor, parentElement: PsiNamedElement): ScType = {
/*      val noTypeInference = parentElement match {
        case t: ScTypeParametersOwner => t.typeParameters.isEmpty
        case p: PsiTypeParameterListOwner => p.getTypeParameters.isEmpty
        case _ => true
      }*/
      val clazz = constr.getContainingClass
      val tp = clazzType(clazz, subst)
      val res = subst.subst(tp)
      lazy val params: Seq[Seq[Parameter]] = constr match {
        case fun: ScFunction =>
          fun.paramClauses.clauses.map(_.parameters.map(p => new Parameter(p.name,
            subst.subst(p.getType(TypingContext.empty).getOrElse(Any)), p.isDefaultParam,
            p.isRepeatedParameter)))
        case f: ScPrimaryConstructor =>
          f.parameterList.clauses.map(_.parameters.map(p => new Parameter(p.name,
            subst.subst(p.getType(TypingContext.empty).getOrElse(Any)), p.isDefaultParam,
            p.isRepeatedParameter)))
        case m: PsiMethod =>
          Seq(m.getParameterList.getParameters.toSeq.map(p => new Parameter("",
            ScType.create(p.getType, getProject, getResolveScope), false, p.isVarArgs)))
      }
      var typeParameters: Seq[TypeParameter] = parentElement match {
        case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 => {
          tp.typeParameters.map(tp => new TypeParameter(tp.name,
                tp.lowerBound.getOrElse(Nothing), tp.upperBound.getOrElse(Any), tp))
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
          val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ (zipped.map{case (arg, tp) =>
            ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), arg.getType(TypingContext.empty).getOrElse(Any))}),
            Map.empty, Map.empty)
          return appSubst.subst(res)
        }
        case _ =>
      }

      findConsturctor match {
        case Some(c) => {
          for (i <- 0 until params.length if i < c.arguments.length) {
            typeParameters = ScalaPsiUtil.localTypeInference(res, params(i), c.arguments(i).exprs.map(new Expression(_)),
              typeParameters, subst).typeParameters
          }
          //todo: last implicit clause

          c.expectedType match {
            case Some(expected) => {
              def updateRes(expected: ScType) {
                typeParameters = ScalaPsiUtil.localTypeInference(res, Seq(Parameter("", expected, false, false)),
                    Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParameters).subst(res.inferValueType))),
                  typeParameters, shouldUndefineParameters = false).typeParameters //here should work in different way:
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

          val pts = new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp =>
            ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
                  if (tp.upperType.equiv(Any)) tp.lowerType else if (tp.lowerType.equiv(Nothing)) tp.upperType
                  else tp.lowerType))),
            Map.empty, Map.empty)
          pts.subst(res)
        }
        case None => res
      }
    }

    reference match {
      case Some(ref) => ref.qualifier match {
        case Some(q) => wrap(ref.bind) flatMap {
          case ScalaResolveResult(aliasDef: ScTypeAliasDefinition, s) => {
            if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
            else lift(new ScTypeConstructorType(aliasDef, s))
          }
          case ScalaResolveResult(synth: ScSyntheticClass, _) => lift(synth.t)
          case r@ScalaResolveResult(method: PsiMethod, subst) => {
            lift(typeForConstructor(method, subst, r.getActualElement))
          }
          case r: ScalaResolveResult => {
            //todo: scalap hack
            if (r.isHacked) {
              q.bind match {
                case Some(ScalaResolveResult(obj: ScObject, subst)) => {
                  ScalaPsiUtil.getCompanionModule(obj) match {
                    case Some(clazz) => lift(ScProjectionType(ScDesignatorType(clazz), ref))
                    case _ => lift(ScProjectionType(new ScSingletonType(q), ref))
                  }
                }
                case _ => lift(ScProjectionType(new ScSingletonType(q), ref))
              }
            } else
              lift(ScProjectionType(new ScSingletonType(q), ref))
          }
        }
        case None => wrap(ref.bind) flatMap {
          case r@ScalaResolveResult(e, s) => e match {
            case aliasDef: ScTypeAliasDefinition =>
              if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
              else lift(new ScTypeConstructorType(aliasDef, s))
            case alias: ScTypeAliasDeclaration => lift(new ScTypeAliasType(alias, s))
            case tp: PsiTypeParameter => lift(ScalaPsiManager.typeVariable(tp))
            case synth: ScSyntheticClass => lift(synth.t)
            case method: PsiMethod => {
              lift(typeForConstructor(method, s, r.getActualElement))
            }
            case null => lift(Any)
            case _ => {
              val clazz = r.boundClass
              if (clazz != null) {
                lift(ScProjectionType(ScDesignatorType(clazz), ref))
              } else lift(ScDesignatorType(e))            }
          }
        }
      }
      case None if singleton => lift(ScSingletonType(pathElement))
      case None => Failure("Reference is not defined", Some(this))
    }
  }
}
