package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import lang.resolve.ScalaResolveResult
import collection.mutable.ArrayBuffer
import api.toplevel.typedef.{ScClass}
import types.result.TypingContext
import types._
import api.statements.params.{ScTypeParam, ScParameter}
import api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import api.base.{ScStableCodeReferenceElement, ScPrimaryConstructor, ScConstructor}
import com.intellij.psi._
import lexer.ScalaTokenTypes
import nonvalue.Parameter

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
                ScType.extractClass(typez) match {
                  case Some(clazz) => {
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

  def invocationCount: Int = {
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
        call.getEffectiveInvokedExpr
      }
      case _ => null
    }
  }

  def matchedParameters: Option[Map[ScExpression, Parameter]] = {
    getContext match {
      case call: ScMethodCall => {
        Some(call.matchedParameters)
      }
      case _ => None
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
                    val tp: Seq[(String, String)] = method match {
                      case fun: ScFunction => {
                        fun.typeParameters.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p)))
                      }
                      case method: PsiMethod => {
                        method.getTypeParameters.map(p => (p.getName, ScalaPsiUtil.getPsiElementId(p)))
                      }
                    }
                    s.followed(ScalaPsiUtil.genericCallSubstitutor(tp, gen))
                  }
                  case _ if method.getTypeParameters.length != 0 => {
                    val subst = method.getTypeParameters.foldLeft(ScSubstitutor.empty) {
                      (subst, tp) => subst.bindT((tp.getName, ScalaPsiUtil.getPsiElementId(tp)), ScUndefinedType(tp match {
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
                              subst.subst(p.getType(TypingContext.empty).getOrAny))}).toArray
                    } else if (invocationCount == 1) buffer += Array.empty
                  }
                  case method: PsiMethod if invocationCount == 1=> {
                    buffer += method.getParameterList.getParameters.map({p: PsiParameter => {
                            val tp: ScType = subst.subst(ScType.create(p.getType, p.getProject, getResolveScope,
                              paramTopLevel = true))
                            ("", if (!p.isVarArgs) tp else tp match {
                              case ScParameterizedType(_, args) if args.length == 1=> args(0)
                              case JavaArrayType(arg) => arg
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
          case elem: ScParameterizedTypeElement => ScType.extractClassType(
            constr.typeElement.getType(TypingContext.empty).getOrAny, Some(elem.getProject)
          )
          case simple: ScSimpleTypeElement => simple.reference match {
            case Some(ref: ScStableCodeReferenceElement) => ref.bind match {
              case Some(ScalaResolveResult(clazz: PsiClass, subst)) => Some((clazz, subst))
              case _ => None
            }
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
                  add += Tuple(param.name, subst.subst(param.getType(TypingContext.empty).getOrAny))
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
                    add += Tuple(param.name, subst.subst(param.getType(TypingContext.empty).getOrAny))
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
                add += Tuple("", subst.subst(ScType.create(param.getType, getProject, getResolveScope,
                  paramTopLevel = true)))
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

  override def addBefore(element: PsiElement, anchor: PsiElement): PsiElement = {
    if (anchor == null) {
      if (exprs.length == 0) {
        val par: PsiElement = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
        if (par == null) return super.addBefore(element, anchor)
        super.addAfter(element, par)
      } else {
        val par: PsiElement = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
        if (par == null) return super.addBefore(element, anchor)
        val comma = ScalaPsiElementFactory.createComma(getManager)
        super.addAfter(par, comma)
        super.addAfter(par, element)
      }
    } else {
      super.addBefore(element, anchor)
    }
  }

  def addExpr(expr: ScExpression): ScArgumentExprList = {
    val par = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
    val nextNode = par.getNode.getTreeNext
    val node = getNode
    val needCommaAndSpace = exprs.nonEmpty
    node.addChild(expr.getNode, nextNode)
    if (needCommaAndSpace) {
      val comma = ScalaPsiElementFactory.createComma(getManager)
      val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
      node.addChild(comma.getNode, nextNode)
      node.addChild(space, nextNode)
    }
    this
  }

  def addExprAfter(expr: ScExpression, anchor: PsiElement): ScArgumentExprList = {
    val nextNode = anchor.getNode.getTreeNext
    val comma = ScalaPsiElementFactory.createComma(getManager)
    val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
    val node = getNode
    if (nextNode != null) {
      node.addChild(comma.getNode, nextNode)
      node.addChild(space, nextNode)
      node.addChild(expr.getNode, nextNode)
    } else {
      node.addChild(comma.getNode)
      node.addChild(space)
      node.addChild(expr.getNode)
    }
    this
  }
}