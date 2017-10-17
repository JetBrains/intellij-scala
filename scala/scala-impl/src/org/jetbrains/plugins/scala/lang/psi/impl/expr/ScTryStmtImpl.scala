package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor

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

  protected override def innerType: TypeResult[ScType] = {
    val lifted = tryBlock.getType()
    lifted flatMap { _ => catchBlock match {
        case None => lifted
        case Some(cb) =>
          cb.expression match {
            case Some(expr) if !lifted.isEmpty =>
              expr.getType() match {
                case Success(_, _) =>
                  val tp = expr.getType().getOrAny
                  val throwable = ScalaPsiManager.instance(expr.getProject).getCachedClass(expr.resolveScope, "java.lang.Throwable")
                  throwable.fold(lifted) { throwable =>
                    val throwableType = ScDesignatorType(throwable)
                    val processor = new MethodResolveProcessor(expr, "apply", List(Seq(new Compatibility.Expression(throwableType))),
                      Seq.empty, Seq.empty)
                    processor.processType(tp, expr)
                    val candidates = processor.candidates
                    if (candidates.length != 1) lifted
                    else {
                      candidates(0) match {
                        case ScalaResolveResult(fun: ScFunction, subst) =>
                          fun.returnType.map(tp => lifted.get.lub(subst.subst(tp), checkWeak = true))
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
