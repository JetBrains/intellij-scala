package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

/**
 * Example: {{{
 *   body match
 *     case '{ sum(${Varargs(args)}: _*) } =>
 *          |----------------------------|
 *                 ScQuotedPattern
 * }}}
 * @see [[org.jetbrains.plugins.scala.lang.psi.api.expr.ScSplicedPatternExpr]]
 */
trait ScQuotedPattern extends ScPattern