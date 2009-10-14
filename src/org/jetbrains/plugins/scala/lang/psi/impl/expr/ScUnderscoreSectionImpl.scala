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
          case Some(expr: ScExpression) => {
            val unders = ScUnderScoreSectionUtil.underscores(expr)
            val i = unders.findIndexOf(_ == this)
            var result: Option[ScType] = null //strange logic to handle problems with detecting type
            var forEqualsParamLength: Boolean = false //this is for working completion
            for (tp <- ExpectedTypes.expectedExprTypes(expr) if result != None) {
              tp match {
                case ScFunctionType(_, params) if params.length >= unders.length => {
                  if (result != null) {
                    if (params.length == unders.length && !forEqualsParamLength) {
                      result = Some(params(i))
                      forEqualsParamLength = true
                    } else if (params.length == unders.length) result = None
                  }
                  else if (params.length > unders.length) result = Some(params(i))
                  else {
                    result = Some(params(i))
                    forEqualsParamLength = true
                  }
                }
                case ScParameterizedType(t, args) => {
                  ScType.extractDesignated(t) match { //todo: this is hack, scala.Function1 ScProjectionType?
                    case Some((c: PsiClass, _)) if c.getQualifiedName.startsWith("scala.Function") &&
                            args.length >= unders.length + 1 => {
                      if (result != null) {
                        if (args.length == unders.length + 1 && !forEqualsParamLength) {
                          result = Some(args(i))
                          forEqualsParamLength = true
                        } else if (args.length == unders.length + 1) result = None
                      }
                      else if (args.length > unders.length + 1) result = Some(args(i))
                      else {
                        result = Some(args(i))
                        forEqualsParamLength = true
                      }
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
        }
      }
    }
  }
}