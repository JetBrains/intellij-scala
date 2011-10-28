package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base._
import api.toplevel.templates.{ScExtendsBlock, ScClassParents}
import api.expr.ScNewTemplateDefinition
import api.toplevel.ScTypeParametersOwner
import collection.immutable.HashMap
import api.statements.{ScTypeAliasDefinition, ScFunction}
import types.ScSimpleTypeElementImpl
import com.intellij.psi.{PsiClass, PsiTypeParameterListOwner, PsiMethod}
import api.statements.params.ScTypeParam
import psi.types._
import nonvalue.{ScTypePolymorphicType, TypeParameter}
import result.{Success, Failure, TypeResult, TypingContext}
import resolve.{ResolveUtils, ScalaResolveResult}
import collection.mutable.ArrayBuffer
import collection.Seq
import api.base.types.{ScTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstructorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstructor {

  def typeElement: ScTypeElement = findNotNullChildByClass(classOf[ScTypeElement])

  override def toString: String = "Constructor"

  def expectedType: Option[ScType] = {
    getContext match {
      case parents: ScClassParents => {
        if (parents.typeElements.length != 1) None
        else {
          parents.getContext match {
            case e: ScExtendsBlock => {
              e.getContext match {
                case n: ScNewTemplateDefinition => {
                  n.expectedType()
                }
                case _ => None
              }
            }
            case _ => None
          }
        }
      }
      case _ => None
    }
  }

  def newTemplate = {
    getContext match {
      case parents: ScClassParents => {
        parents.getContext match {
          case e: ScExtendsBlock => {
            e.getContext match {
              case n: ScNewTemplateDefinition => {
                Some(n)
              }
              case _ => None
            }
          }
        }
      }
      case _ => None
    }
  }

  //todo: duplicate ScSimpleTypeElementImpl
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

  def shapeType(i: Int): TypeResult[ScType] = {
    def FAILURE = Failure("Can't resolve type", Some(this))
    val seq = shapeMultiType(i)
    if (seq.length == 1) seq(0)
    else FAILURE
  }

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]] = innerMultiType(i, true)

  def multiType(i: Int): Seq[TypeResult[ScType]] = innerMultiType(i, false)

  private def innerMultiType(i: Int, isShape: Boolean): Seq[TypeResult[ScType]] = {
    def FAILURE = Failure("Can't resolve type", Some(this))
    def workWithResolveResult(constr: PsiMethod, r: ScalaResolveResult,
                              subst: ScSubstitutor, s: ScSimpleTypeElement,
                              ref: ScStableCodeReferenceElement): TypeResult[ScType] = {
      val clazz = constr.getContainingClass
      val tp = r.getActualElement match {
        case ta: ScTypeAliasDefinition => subst.subst(ta.aliasedType.getOrElse(return FAILURE))
        case _ =>
          parameterize(ScSimpleTypeElementImpl.calculateReferenceType(ref, true).
            getOrElse(return FAILURE), clazz, subst)
      }
      val res = constr match {
        case fun: ScMethodLike =>
          val methodType = ScType.nested(fun.methodType(Some(tp)), i).getOrElse(return FAILURE)
          subst.subst(methodType)
        case method: PsiMethod =>
          if (i > 0) return Failure("Java constructors only have one parameter section", Some(this))
          ResolveUtils.javaMethodType(method, subst, getResolveScope, Some(subst.subst(tp)))
      }
      val typeParameters: Seq[TypeParameter] = r.getActualElement match {
        case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 => {
          tp.typeParameters.map(tp =>
            new TypeParameter(tp.name,
              tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
        }
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.length > 0 => {
          ptp.getTypeParameters.toSeq.map(ptp =>
            new TypeParameter(ptp.getName,
              Nothing, Any, ptp)) //todo: add lower and upper bound
        }
        case _ => return Success(res, Some(this))
      }
      s.getParent match {
        case p: ScParameterizedTypeElement => {
          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ (zipped.map {
            case (arg, tp) =>
              ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), arg.getType(TypingContext.empty).getOrAny)
          }), Map.empty, None)
          Success(appSubst.subst(res), Some(this))
        }
        case _ => Success(ScTypePolymorphicType(res, typeParameters), Some(this))
      }
    }
    def processSimple(s: ScSimpleTypeElement): Seq[TypeResult[ScType]] = {
      s.reference match {
        case Some(ref) => {
          val buffer = new ArrayBuffer[TypeResult[ScType]]
          val resolve = if (isShape) ref.shapeResolveConstr else ref.resolveAllConstructors
          resolve.foreach(r => r match {
            case r@ScalaResolveResult(constr: PsiMethod, subst) => {
              buffer += workWithResolveResult(constr, r, subst, s, ref)
            }
            case _ =>
          })
          buffer.toSeq
        }
        case _ => Seq(Failure("Hasn't reference", Some(this)))
      }
    }

    typeElement match {
      case s: ScSimpleTypeElement => processSimple(s)
      case p: ScParameterizedTypeElement => {
        p.typeElement match {
          case s: ScSimpleTypeElement => processSimple(s)
          case _ => Seq.empty
        }
      }
      case _ => Seq.empty
    }
  }

  def reference: Option[ScStableCodeReferenceElement] = {
    typeElement match {
      case s: ScSimpleTypeElement =>
        s.reference
      case p: ScParameterizedTypeElement =>
        p.typeElement match {
          case s: ScSimpleTypeElement =>
            s.reference
          case _ => None
        }
      case _ => None
    }
  }
}