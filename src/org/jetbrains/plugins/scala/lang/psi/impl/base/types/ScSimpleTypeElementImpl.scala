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
import nonvalue.{TypeParameter, Parameter}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import result.{Failure, TypeResult, Success, TypingContext}
import scala.None
import api.statements._
import api.toplevel.ScTypeParametersOwner
import params.ScTypeParam
import psi.types.Compatibility.Expression
import collection.immutable.HashMap
import api.expr.{ScSuperReference, ScThisReference, ScUnderScoreSectionUtil}
import api.base.{ScPathElement, ScStableCodeReferenceElement, ScPrimaryConstructor, ScConstructor}
import lang.resolve.ScalaResolveResult

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

    def typeForConstructor(ref: ScStableCodeReferenceElement, constr: PsiMethod, subst: ScSubstitutor, parentElement: PsiNamedElement): ScType = {
      /* val noTypeInference = parentElement match {
           case t: ScTypeParametersOwner => t.typeParameters.isEmpty
           case p: PsiTypeParameterListOwner => p.getTypeParameters.isEmpty
           case _ => true
         }*/
      val clazz = constr.getContainingClass
      val tp = parentElement match {
        case ta: ScTypeAliasDefinition => subst.subst(ta.aliasedType.getOrElse(return Nothing))
        case _ => parameterize(ScSimpleTypeElementImpl.calculateReferenceType(ref, false).getOrElse(return Nothing), clazz, subst)
      }
      val res = subst.subst(tp)
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
            Map.empty, None)
          return appSubst.subst(res)
        }
        case _ =>
      }


      val params: Seq[Seq[Parameter]] = constr match {
        case fun: ScFunction =>
          fun.paramClauses.clauses.map(_.parameters.map(p => new Parameter(p.name,
            subst.subst(p.getType(TypingContext.empty).getOrElse(Any)), p.isDefaultParam,
            p.isRepeatedParameter, p.isCallByNameParameter)))
        case f: ScPrimaryConstructor =>
          f.parameterList.clauses.map(_.parameters.map(p => new Parameter(p.name,
            subst.subst(p.getType(TypingContext.empty).getOrElse(Any)), p.isDefaultParam,
            p.isRepeatedParameter, p.isCallByNameParameter)))
        case m: PsiMethod =>
          Seq(m.getParameterList.getParameters.toSeq.map(p => new Parameter("",
            ScType.create(p.getType, getProject, getResolveScope, paramTopLevel = true), false, p.isVarArgs, false)))
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
                typeParameters = ScalaPsiUtil.localTypeInference(res, Seq(new Parameter("", expected, false, false, false)),
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
            Map.empty, None)
          pts.subst(res)
        }
        case None => res
      }
    }

    reference match {
      case Some(ref) => ref.bind() match {
        case Some(r@ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) => {
          Success(typeForConstructor(ref, method, subst, r.getActualElement), Some(this))
        }
        case Some(ScalaResolveResult(tp: PsiTypeParameter, _)) => lift(ScalaPsiManager.typeVariable(tp))
        case Some(r@ScalaResolveResult(synth: ScSyntheticClass, _)) => lift(synth.t)
        case _ => ScSimpleTypeElementImpl.calculateReferenceType(ref, false)
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
    val (resolvedElement, subst, fromType) = (if (!shapesOnly) ref.bind() else {
      ref.shapeResolve match {
        case Array(r: ScalaResolveResult) => Some(r)
        case _ => None
      }
    }) match {
      case Some(r@ScalaResolveResult(n: PsiMethod, subst)) if n.isConstructor =>
        (n.getContainingClass, subst, r.fromType)
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