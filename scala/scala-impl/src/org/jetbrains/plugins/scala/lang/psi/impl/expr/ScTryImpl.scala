package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Alexander Podkhalyuzin
  */
class ScTryImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTry {

  import ScTryImpl._

  protected override def innerType: TypeResult =
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
            .map(substitutor)
            .map(tryBlockType.lub(_))
        case _ => Right(tryBlockType)
      }
    }

  override def toString: String = "TryStatement"
}

object ScTryImpl {

  private def createProcessor(expression: ScExpression)
                             (implicit projectContext: ProjectContext): Option[MethodResolveProcessor] =
    expression.elementScope.getCachedClass("java.lang.Throwable")
      .map(ScDesignatorType(_))
      .map(new Compatibility.Expression(_))
      .map { compatibilityExpression =>
        new MethodResolveProcessor(expression, "apply", List(Seq(compatibilityExpression)), Seq.empty, Seq.empty)
      }
}
