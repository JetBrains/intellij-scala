package org.jetbrains.plugins.scala.lang

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr}

package object formatting {

  private[formatting]
  implicit class ScArgumentExprListOps(private val args: ScArgumentExprList) extends AnyVal {

    /**
     * @example {{{
     * seq.map { p =>
     *   ...
     * }
     * }}}
     *
     */
    def isSingleInfixBlockExpression: Boolean =
      args match {
        case ScArgumentExprList(_: ScBlockExpr) =>
          // no need to also check for last child, cause parser will not capture it without opening parenthesis
          val firstChild = args.firstChild
          firstChild.forall(_.elementType != ScalaTokenTypes.tLPARENTHESIS)
        case _ =>
          false
      }
  }

}
