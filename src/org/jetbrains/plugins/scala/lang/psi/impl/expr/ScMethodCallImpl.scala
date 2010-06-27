package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.base.ScStableCodeReferenceElement
import api.statements.{ScFunction, ScFun}
import api.toplevel.ScTypedDefinition
import api.toplevel.typedef.{ScClass, ScObject}
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction}
import api.base.types.ScTypeElement
import types._
import com.intellij.psi._
import nonvalue.{TypeParameter, Parameter, ScMethodType, ScTypePolymorphicType}
import result.{TypeResult, Failure, Success, TypingContext}
import implicits.ScImplicitlyConvertible
import api.toplevel.imports.usages.ImportUsed
import com.intellij.openapi.progress.ProgressManager
import lang.resolve.{ResolveUtils, ScalaResolveResult}
import api.statements.params.{ScTypeParam, ScParameters}
import types.Compatibility.Expression
import lang.resolve.processor.{BaseProcessor, ResolverEnv, MethodResolveProcessor}
import caches.CachesUtil
import annotator.ScalaAnnotator

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMethodCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMethodCall {
  override def toString: String = "MethodCall"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    var nonValueType = getInvokedExpr.getNonValueType(TypingContext.empty)
    val res = nonValueType match {
      case Success(ScFunctionType(retType: ScType, params: Seq[ScType]), _) => retType
      case Success(ScMethodType(retType, _, _), _) => retType
      case Success(ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams), _) => {
        val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
        ScalaPsiUtil.localTypeInference(retType, params, exprs, typeParams)
      }
      case Success(tp: ScType, _) => ScalaPsiUtil.processTypeForUpdateOrApply(tp, this, false).getOrElse(Nothing) match {
        case ScFunctionType(retType: ScType, params: Seq[ScType]) => retType
        case ScMethodType(retType, _, _) => retType
        case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) => {
          val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
          ScalaPsiUtil.localTypeInference(retType, params, exprs, typeParams)
        }
        case tp => tp
      }
      case x => return x
    }

    Success(res, Some(this))
  }


}