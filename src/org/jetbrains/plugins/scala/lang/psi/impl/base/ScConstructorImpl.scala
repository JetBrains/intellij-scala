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
import api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}
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

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstructorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstructor {

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
                  n.expectedType
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
    if (i > 0) return Failure("doesn't implemented yet", Some(this)) //todo: implement me
    def FAILURE = Failure("Can't resolve type", Some(this))
    def processSimple(s: ScSimpleTypeElement): TypeResult[ScType] = {
      s.reference match {
        case Some(ref) => {
          ref.shapeResolve match {
            case Array(r@ScalaResolveResult(constr: PsiMethod, subst)) => {
              val clazz = constr.getContainingClass
              val tp = r.getActualElement match {
                case ta: ScTypeAliasDefinition => subst.subst(ta.aliasedType.getOrElse(return FAILURE))
                case _ => parameterize(ScSimpleTypeElementImpl.calculateReferenceType(ref, true).getOrElse(return FAILURE), clazz, subst)
              }
              val res = constr match {
                case fun: ScFunction => subst.subst(fun.methodType(Some(tp)))
                case fun: ScPrimaryConstructor => subst.subst(fun.methodType(Some(tp)))
                case method: PsiMethod => ResolveUtils.javaMethodType(method, subst, getResolveScope, Some(subst.subst(tp)))
              }
              var typeParameters: Seq[TypeParameter] = r.getActualElement match {
                case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 => {
                  tp.typeParameters.map(tp => new TypeParameter(tp.name,
                    tp.lowerBound.getOrElse(Nothing), tp.upperBound.getOrElse(Any), tp))
                }
                case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.length > 0 => {
                  ptp.getTypeParameters.toSeq.map(ptp => new TypeParameter(ptp.getName,
                    Nothing, Any, ptp)) //todo: add lower and upper bound
                }
                case _ => return Success(res, Some(this))
              }
              s.getParent match {
                case p: ScParameterizedTypeElement => {
                  val zipped = p.typeArgList.typeArgs.zip(typeParameters)
                  val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ (zipped.map {
                    case (arg, tp) =>
                      ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), arg.getType(TypingContext.empty).getOrElse(Any))
                  }), Map.empty)
                  return Success(appSubst.subst(res), Some(this))
                }
                case _ => return Success(ScTypePolymorphicType(res, typeParameters), Some(this))
              }

            }
            case _ => Failure("Can't resolve", Some(this))
          }
        }
        case _ => Failure("Hasn't reference", Some(this))
      }
    }

    typeElement match {
      case s: ScSimpleTypeElement => processSimple(s)
      case p: ScParameterizedTypeElement => {
        p.typeElement match {
          case s: ScSimpleTypeElement => processSimple(s)
          case _ => FAILURE
        }
      }
      case _ => FAILURE
    }
  }
}