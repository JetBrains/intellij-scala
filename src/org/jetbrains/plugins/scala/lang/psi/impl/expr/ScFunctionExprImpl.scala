package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.statements.params.{ScParameter, ScParameters}
import types.result.TypingContext
import psi.controlFlow.impl.ScalaControlFlowBuilder
import psi.controlFlow.Instruction;
import types._
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * @author Alexander Podkhalyuzin
 */

class ScFunctionExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionExpr {
  override def toString: String = "FunctionExpression"

  def parameters = params.params

  def params = findChildByClass(classOf[ScParameters])

  def result = findChild(classOf[ScExpression])

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    result match {
      case Some(x) if x == lastParent => {
        for (p <- parameters) {
          if (!processor.execute(p, state)) return false
        }
        true
      }
      case _ => true
    }
  }

  protected override def innerType(ctx: TypingContext) = {
    val paramTypes = (parameters: Seq[ScParameter]).map(_.getType(ctx))
    wrap(result)(ScalaBundle.message("no.result.expression.found")) flatMap {r =>
      collectFailures(paramTypes, Nothing)(new ScFunctionType(r.getType(ctx).getOrElse(Any), _, getProject))
    }
  }

  private var myControlFlow: Seq[Instruction] = null

  def getControlFlow(cached: Boolean) = {
    if (!cached || myControlFlow == null) result match {
      case Some(e) => {
        val builder = new ScalaControlFlowBuilder(null, null)
        myControlFlow = builder.buildControlflow(e)
      }
      case None =>
    }
    myControlFlow
  }

}