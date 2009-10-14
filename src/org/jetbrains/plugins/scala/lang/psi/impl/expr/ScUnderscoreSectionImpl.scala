package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import types.{ScParameterizedType, ScFunctionType, ScType}
import com.intellij.psi.PsiClass

/**
* @author Alexander Podkhalyuzin, ilyas
*/

class ScUnderscoreSectionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnderscoreSection {
  override def toString: String = "UnderscoreSection"

  protected override def innerType(): ScType = {
    bindingExpr match {
      case Some(x) => psi.types.Nothing //todo: implement me
      case None => {
        getParent match {
          case typed: ScTypedStmt => return typed.getType
          case _ =>
        }
        overExpr match {
          case None => psi.types.Nothing
          case Some(expr: ScExpression) => { //todo: code duplicate with changes: ScParameterImpl.expectedParamType
            val unders = ScUnderScoreSectionUtil.underscores(expr)
            val i = unders.findIndexOf(_ == this)
            ExpectedTypes.expectedForArguments(expr) match {
              case Some(args: ScArgumentExprList) => {
                val expression: ScExpression = args.exprs.find({e: ScExpression => e.getTextRange.
                        containsRange(expr.getTextRange.getStartOffset, expr.getTextRange.getEndOffset)}).getOrElse(null)
                val j = args.exprs.indexOf(expression)
                val name = expression match {
                  case assign: ScAssignStmt => assign.getLExpression.getText
                  case _ => ""
                }
                var result: Option[ScType] = null //strange logic to handle problems with detecting type
                for (application: Array[(String, ScType)] <- args.possibleApplications if result != None) {
                  val tp: ScType = name match {
                    case "" => {
                      if (application.length > j) application(j)._2
                      else psi.types.Nothing
                    }
                    case _ => application.find(_._1 == name) match {
                      case None => psi.types.Nothing
                      case Some(t) => t._2
                    }
                  }
                  tp match {
                    case ScFunctionType(_, params) if params.length >= unders.length => {
                      if (result != null) {
                        if (params.length == unders.length) result = Some(params(i))
                      }
                      else result = Some(params(i))
                    }
                    case ScParameterizedType(t, args) => {
                      ScType.extractDesignated(t) match { //todo: this is hack, scala.Function1 ScProjectionType?
                        case Some((c: PsiClass, _)) if c.getQualifiedName.startsWith("scala.Function") &&
                                args.length >= unders.length + 1 => {
                          if (result != null) {
                            if (args.length == unders.length + 1) result = Some(args(i))
                          }
                          else result = Some(args(i))
                        }
                        case _ =>
                      }
                    }
                    case _ =>
                  }
                }
                if (result == null) result = None
                result.getOrElse(psi.types.Nothing)
              }
              case Some(expr: ScExpression) => {
                expr.getType match {
                  case ScFunctionType(rt, _) => {
                    rt match {
                      case ScFunctionType(_, params) =>
                        if (i >= 0 && i < params.length) params(i) else psi.types.Nothing
                      case _ => psi.types.Nothing
                    }
                  }
                  case _ => psi.types.Nothing
                }
              }
              case _ => {
                val option: Option[ScType] = expr.expectedType
                option match {
                  case Some(ScFunctionType(_, params)) =>
                    if (i >= 0 && i < params.length) params(i) else psi.types.Nothing
                  case _ => psi.types.Nothing
                }
              }
            }
          }
        }
      }
    }
  }
}