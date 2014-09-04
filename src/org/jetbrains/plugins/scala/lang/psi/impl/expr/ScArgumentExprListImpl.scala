package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScArgumentExprListImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScArgumentExprList {
  override def toString: String = "ArgumentList"

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

  def matchedParameters: Option[Seq[(ScExpression, Parameter)]] = {
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
                        method.getTypeParameters.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p)))
                      }
                    }
                    s.followed(ScalaPsiUtil.genericCallSubstitutor(tp, gen))
                  }
                  case _ if method.getTypeParameters.length != 0 =>
                    val subst = method.getTypeParameters.foldLeft(ScSubstitutor.empty) {
                      (subst, tp) => subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp)), ScUndefinedType(tp match {
                        case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, s)
                        case tp: PsiTypeParameter => new ScTypeParameterType(tp, s)
                      }))
                    }
                    s.followed(subst)
                  case _ => s
                }
                method match {
                  case fun: ScFunction =>
                    if (fun.paramClauses.clauses.length >= invocationCount) {
                      buffer += fun.paramClauses.clauses.apply(invocationCount - 1).parameters.map({p => (p.name,
                              subst.subst(p.getType(TypingContext.empty).getOrAny))}).toArray
                    } else if (invocationCount == 1) buffer += Array.empty
                  case method: PsiMethod if invocationCount == 1 =>
                    buffer += method.getParameterList.getParameters.map({p: PsiParameter => ("", subst.subst(p.exactParamType()))})
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
            case Some(ref: ScStableCodeReferenceElement) =>
              ref.bind() match {
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
                  add += ((param.name, subst.subst(param.getType(TypingContext.empty).getOrAny)))
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
                    add += ((param.name, subst.subst(param.getType(TypingContext.empty).getOrAny)))
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
                add += (("", subst.subst(param.paramType)))
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