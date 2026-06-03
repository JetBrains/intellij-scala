package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.PatternTypeInference
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

abstract class ScExtractorPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScExtractorPattern {
  override def `type`(): TypeResult =
    ref.bind() match {
      case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.isUnapplyMethod && fun.parameters.count(!_.isImplicit) == 1 =>
        val expectedType = this.expectedType
        val subst =
          expectedType.fold(
            ScSubstitutor.empty
          )(PatternTypeInference.doTypeInference(this, _))

        fun.paramClauses.clauses.head.parameters.head.`type`().map(subst)
      case _ =>
        Failure(ScalaBundle.message("cannot.resolve.unknown.symbol"))
    }

  override def isIrrefutableForImpl(scrutineeType: ScType, deep: Boolean): Boolean = {
    val target = targetFor(Some(scrutineeType))

    def subpatternsMatch = target.flatMap(_.argPatternsMapping).forall { mapping =>
      subpatterns.forall { p =>
        val x = mapping.typeOfArg(p).getOrElse(StdTypes.instance.Any)
        p.isIrrefutableFor(x, deep)
      }
    }

    target.exists(_.isIrrefutable) &&
      (!deep || subpatternsMatch)
  }
}
