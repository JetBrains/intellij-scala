package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import resolve.ScalaResolveResult
import collection.mutable.ArrayBuffer
import api.toplevel.typedef.{ScClass}
import types.result.TypingContext
import types._
import api.statements.params.{ScTypeParam, ScParameter}
import com.intellij.psi.{PsiTypeParameter, PsiParameter, PsiMethod, PsiClass}
import api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import api.base.{ScStableCodeReferenceElement, ScPrimaryConstructor, ScConstructor}

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScArgumentExprListImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScArgumentExprList {
  override def toString: String = "ArgumentList"

  def nameCallFromParameter: Int = {
    var i = 0
    for (expr <- exprs) {
      expr match {
        case assign: ScAssignStmt => {
          assign.assignName match {
            case Some(name: String) => {
              callReference match {
                case Some(ref: ScReferenceExpression) => {
                  val count = invocationCount
                  val variants = ref.getSameNameVariants.map(r => r.getElement)
                  for {
                    variant <- variants
                    if variant.isInstanceOf[ScFunction]
                    if variant.asInstanceOf[ScFunction].hasParamName(name, count)
                  } {
                    return i
                  }
                }
                case None =>
              }
              val types = callExpression.allTypes
              for (typez <- types) {
                ScType.extractClassType(typez) match {
                  case Some((clazz: PsiClass, _)) => {
                    val applyMethods = clazz.findMethodsByName("apply", true)
                    for{
                      method <- applyMethods
                      if method.isInstanceOf[ScFunction]
                      function = method.asInstanceOf[ScFunction]
                      if function.hasParamName(name)
                    } {
                      return i
                    }
                  }
                  case None =>
                }
              }
            }
            case None =>
          }
        }
        case _ =>
      }
      i = i + 1
    }
    return -1
  }

  lazy val invocationCount: Int = {
    callExpression match {
      case call: ScMethodCall => call.args.invocationCount + 1
      case _ => 1
    }
  }

  def callReference: Option[ScReferenceExpression] = {
    getContext match {
      case call: ScMethodCall =>{
        call.deepestInvokedExpr match {
          case ref: ScReferenceExpression => Some(ref)
          case gen: ScGenericCall => {
            gen.referencedExpr match {
              case ref: ScReferenceExpression => Some(ref)
              case _ => None
            }
          }
          case _ => None
        }
      }
      case _ => None
    }
  }

  def callGeneric: Option[ScGenericCall] = {
    getContext match {
      case call: ScMethodCall => {
        call.deepestInvokedExpr match {
          case gen: ScGenericCall => Some(gen)
          case _ => None
        }
      }
      case _ => None
    }
  }

  def callExpression: ScExpression = {
    getContext match {
      case call: ScMethodCall => {
        call.getInvokedExpr
      }
      case _ => null
    }
  }

  def possibleApplications: Array[Array[(String, ScType)]] = {
    getContext match {
      case call: ScMethodCall => {
        val refOpt = callReference /*: ScReferenceExpression = call.getInvokedExpr match {
          case ref: ScReferenceExpression => ref
          case gen: ScGenericCall => {
            gen.referencedExpr match {
              case ref: ScReferenceExpression => ref
              case _ => null
            }
          }
          case _ => null
        }*/
        val buffer = new ArrayBuffer[Array[(String, ScType)]]
        if (refOpt == None) {
          //todo: according to type: apply or update methods, case classes
        } else {
          val ref = refOpt.get
          val variants = ref.getSameNameVariants
          for (variant <- variants) {
            variant match {
              case ScalaResolveResult(method: PsiMethod, s: ScSubstitutor) => {
                val subst = callGeneric match { //needs for generic call => substitutor more complex
                  case Some(gen) => {
                    val tp: Seq[String] = method match {
                      case fun: ScFunction => {
                        fun.typeParameters.map(_.name)
                      }
                      case method: PsiMethod => {
                        method.getTypeParameters.map(_.getName)
                      }
                    }
                    s.followed(ScalaPsiUtil.genericCallSubstitutor(tp, gen))
                  }
                  case _ if method.getTypeParameters.length != 0 => {
                    val subst = method.getTypeParameters.foldLeft(ScSubstitutor.empty) {
                      (subst, tp) => subst.bindT(tp.getName, ScUndefinedType(tp match {
                        case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, s)
                        case tp: PsiTypeParameter => new ScTypeParameterType(tp, s)
                      }))
                    }
                    s.followed(subst)
                  }
                  case _ => s
                }
                method match {
                  case fun: ScFunction => {
                    if (fun.paramClauses.clauses.length >= invocationCount) {
                      buffer += fun.paramClauses.clauses.apply(invocationCount - 1).parameters.map({p => (p.name,
                              subst.subst(p.getType(TypingContext.empty).getOrElse(Any)))}).toArray
                    } else if (invocationCount == 1) buffer += Array.empty
                  }
                  case method: PsiMethod if invocationCount == 1=> {
                    buffer += method.getParameterList.getParameters.map({p: PsiParameter => {
                            val tp: ScType = subst.subst(ScType.create(p.getType, p.getProject))
                            ("", if (!p.isVarArgs) tp else tp match {
                              case ScParameterizedType(_, args) if args.length == 1=> args(0)
                              case _ => tp
                            })
                          }})
                  }
                  case _ =>
                }
              }
              case _ => //todo: other options
            }
          }
        }
        buffer.toArray
      }
      case constr: ScConstructor => {
        val res = new ArrayBuffer[Array[(String, ScType)]]
        val i: Int = constr.arguments.indexOf(this)
        val extract: Option[(PsiClass, ScSubstitutor)] = constr.typeElement match {
          case _: ScParameterizedTypeElement => ScType.extractClassType(constr.typeElement.getType(TypingContext.empty).getOrElse(Any))
          case simple: ScSimpleTypeElement => simple.reference match {
            case Some(ref: ScStableCodeReferenceElement) => ref.bind match {
              case Some(ScalaResolveResult(clazz: PsiClass, subst)) => Some((clazz, subst))
              case _ => None
            }
            case _ => None
          }
          case proj: ScProjectionType => proj.resolveResult match {
            case Some(ScalaResolveResult(clazz: PsiClass, subst)) => Some((clazz, subst))
            case _ => None
          }
          case _ => None //todo: Singleton type, Tuple type?
        }
        extract match {
          case Some((clazz: ScClass, subst: ScSubstitutor)) => {
            for (function: ScFunction <- clazz.functions if function.isConstructor) {
              val clauses = function.paramClauses.clauses
              if (i < clauses.length) {
                val add: ArrayBuffer[(String, ScType)] = new ArrayBuffer
                val clause = clauses(i)
                for (param: ScParameter <- clause.parameters) {
                  add += Tuple(param.name, subst.subst(param.getType(TypingContext.empty).getOrElse(Any)))
                }
                res += add.toArray
              }
            }
            clazz.constructor match {
              case Some(constr: ScPrimaryConstructor) => {
                val clauses = constr.parameterList.clauses
                if (i < clauses.length) {
                  val add: ArrayBuffer[(String, ScType)] = new ArrayBuffer
                  val clause = clauses(i)
                  for (param: ScParameter <- clause.parameters) {
                    add += Tuple(param.name, subst.subst(param.getType(TypingContext.empty).getOrElse(Any)))
                  }
                  res += add.toArray
                }
              }
              case None =>
            }
          }
          case Some((clazz: PsiClass, subst: ScSubstitutor)) if i == 0 => { //if i > 0 then it cannot be Java Class
            for (constr: PsiMethod <- clazz.getConstructors) {
              val add: ArrayBuffer[(String, ScType)] = new ArrayBuffer
              for (param: PsiParameter <- constr.getParameterList.getParameters) {
                add += Tuple("", subst.subst(ScType.create(param.getType, getProject)))
              }
              res += add.toArray
            }
          }
          case _ =>
        }
        res.toArray
      }
      case _ => Array.empty//todo: constructor
    }
  }
}