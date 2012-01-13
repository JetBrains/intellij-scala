package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor
import types.result.{Success, TypeResult, TypingContext}
import resolve.processor.MethodResolveProcessor
import types.{ScDesignatorType, Compatibility, Bounds, ScType}
import api.statements.ScFunction
import resolve.ScalaResolveResult

/**
* @author Alexander Podkhalyuzin
*/

class ScTryStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTryStmt {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "TryStatement"


  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val lifted = tryBlock.getType(ctx)
    lifted flatMap { result => catchBlock match {
        case None => lifted
        case Some(cb) => {
          cb.expression match {
            case Some(expr) if !lifted.isEmpty =>
              expr.getType(TypingContext.empty) match {
                case Success(tp, _) =>
                  val tp = expr.getType(TypingContext.empty).getOrAny
                  val throwable = ScalaPsiManager.instance(expr.getProject).getCachedClass(expr.getResolveScope, "java.lang.Throwable")
                  if (throwable == null) lifted
                  else {
                    val throwableType = ScDesignatorType(throwable)
                    val processor = new MethodResolveProcessor(expr, "apply", List(Seq(new Compatibility.Expression(throwableType))),
                      Seq.empty, Seq.empty)
                    processor.processType(tp, expr)
                    val candidates = processor.candidates
                    if (candidates.length != 1) lifted
                    else {
                      candidates(0) match {
                        case ScalaResolveResult(fun: ScFunction, subst) =>
                          fun.returnType.map(tp => Bounds.weakLub(lifted.get, subst.subst(tp)))
                        case _ => lifted
                      }
                    }
                  }
                case _ => lifted
              }
            case _ => lifted
          }
        }
      }
    }
  }
}