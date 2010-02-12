package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types._
import result.{TypeResult, Failure, TypingContext, Success}
import java.lang.String
import api.base.{ScPrimaryConstructor, ScStableCodeReferenceElement, ScConstructor}
import api.statements.params.{ScParameter, ScTypeParam}
import api.toplevel.typedef.ScClass
import api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import com.intellij.psi.{PsiClass, PsiParameter, PsiMethod, PsiTypeParameter}
import resolve.ScalaResolveResult
import collection.mutable.ArrayBuffer
import api.statements.ScFunction

/**
 * @author ilyas, Alexander Podkhalyuzin
 */
class ScTupleImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTuple {
  override def toString: String = "Tuple"

  protected[expr] override def innerType(ctx: TypingContext): TypeResult[ScType] =
    if (exprs.length == 0) Success(Unit, Some(this))
    else {
      Success(new ScTupleType(exprs.map(_.getType(ctx).getOrElse(return Failure("Some components Failed to infer", Some(this)))), getProject), Some(this))
    }

  def possibleApplications: Array[Array[(String, ScType)]] = {
    getContext match {
      case call: ScInfixExpr if isCall => {
        call.possibleApplications
      }
      case _ => Array.empty //todo: constructor
    }
  }
}