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

  override def expression: Option[ScExpression] = findChild(classOf[ScExpression])

  override def catchBlock: Option[ScCatchBlock] = findChild(classOf[ScCatchBlock])

  override def finallyBlock: Option[ScFinallyBlock] = findChild(classOf[ScFinallyBlock])

  protected override def innerType: TypeResult =
    expression.map(_.`type`().flatMap { tryBlockType =>
      val maybeExpression = catchBlock.flatMap(_.expression)

      val candidates = for {
        expr <- maybeExpression.toSeq
        (tp, processor) <- expr.`type`().toOption.zip(createProcessor(expr)).toSeq
        candidate <- {
          processor.processType(tp, expr)
          processor.candidates
        }
      } yield candidate

      candidates match {
        case Seq(ScalaResolveResult(function: ScFunction, substitutor)) =>
          function.returnType
            .map(substitutor)
            .map(tryBlockType.lub(_))
        case _ => Right(tryBlockType)
      }
    }).getOrElse(Failure(ScalaBundle.message("nothing.to.type")))

  override def toString: String = "TryStatement"
}

object ScTryImpl {

  private def createProcessor(expression: ScExpression)
                             (implicit projectContext: ProjectContext): Option[MethodResolveProcessor] =
    expression.elementScope.getCachedClass("java.lang.Throwable")
      .map(ScDesignatorType(_))
      .map(Compatibility.Expression(_))
      .map { compatibilityExpression =>
        new MethodResolveProcessor(expression, "apply", List(Seq(compatibilityExpression)), Seq.empty, Seq.empty)
      }
}
