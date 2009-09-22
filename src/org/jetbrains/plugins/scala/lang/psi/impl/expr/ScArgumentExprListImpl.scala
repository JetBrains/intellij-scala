package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import types.ScType
import com.intellij.psi.{PsiElement, PsiClass}

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

  def invocationCount: Int = {
    callExpression match {
      case call: ScMethodCall => return call.args.invocationCount + 1
      case _ => return 1
    }
  }

  def callReference: Option[ScReferenceExpression] = {
    getParent match {
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
    getParent match {
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
    getParent match {
      case call: ScMethodCall => {
        call.getInvokedExpr
      }
      case _ => null
    }
  }
}