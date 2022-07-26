package org.jetbrains.plugins.scala.lang.psi.api.expr

/**
 * Example: {{{
 *   body match
 *     case '{ sum(${Varargs(args)}: _*) } =>
 *                 |--------------|
 *                 ScSplicedPatternExpr
 * }}}
 *
 * @see [[org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScQuotedPattern]]
 */
trait ScSplicedPatternExpr
  extends ScExpression
    with ScSpliced