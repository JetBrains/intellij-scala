package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.project.ProjectContext

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

  import ScTryStmtImpl._

  protected override def innerType: TypeResult[ScType] =
    tryBlock.`type`().flatMap { tryBlockType =>
      val maybeExpression = catchBlock.flatMap(_.expression)

      val candidates = maybeExpression.toSeq.flatMap { expr =>
        expr.`type`().toOption.zip(createProcessor(expr)).flatMap {
          case (tp, processor) =>
            processor.processType(tp, expr)
            processor.candidates
        }
      }

      candidates match {
        case Seq(ScalaResolveResult(function: ScFunction, substitutor)) =>
          function.returnType
            .map(substitutor.subst)
            .map(tryBlockType.lub(_))
        case _ => Right(tryBlockType)
      }
    }
}

object ScTryStmtImpl {

  private def createProcessor(expression: ScExpression)
                             (implicit projectContext: ProjectContext): Option[MethodResolveProcessor] =
    expression.elementScope.getCachedClass("java.lang.Throwable")
      .map(ScDesignatorType(_))
      .map(new Compatibility.Expression(_))
      .map { compatibilityExpression =>
        new MethodResolveProcessor(expression, "apply", List(Seq(compatibilityExpression)), Seq.empty, Seq.empty)
      }
}
