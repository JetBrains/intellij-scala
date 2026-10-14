package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * How the arguments of an invocation are checked, when resolving the invoked method
 * (see [[https://scala-lang.org/files/archive/spec/2.13/06-expressions.html#overloading-resolution overloading resolution]]).
 *
 * The modes are ordered, so that none of them depends on itself:
 * {{{
 * Shape  <-  Narrow  <-  the parameter types of a deferred argument  <-  Full
 * }}}
 * i.e. the parameter types of a function literal with missing parameter types (see [[ResolveMode.isDeferred]])
 * are derived from the alternatives, that survive [[ResolveMode.Narrow]] resolve of the invocation it is an argument of,
 * which doesn't type the function literal, while [[ResolveMode.Full]] resolve does.
 */
sealed trait ResolveMode

object ResolveMode {

  /**
   * The arguments are only checked by their shapes, nothing is typed.
   */
  case object Shape extends ResolveMode

  /**
   * The arguments are typed, except for the deferred ones (see [[isDeferred]]), which are only checked by their shapes.
   *
   * Used to derive the expected types of the deferred arguments in Scala 3, where the alternatives of an overloaded method
   * are narrowed by the other arguments, before the parameter types of function literals are derived from the remaining
   * ones (see `resolveOverloaded` / `pretypeArgs` in dotty's `Applications`), and where the type parameters, the
   * parameter types depend on, may be determined by the other arguments.
   *
   * Note, that the deferred arguments are checked by their shapes in all the argument clauses, so this is only used
   * for the deferred arguments of the first argument clause. The expected types of the arguments of the following
   * clauses are derived from the type of the invocation of the preceding ones.
   */
  case object Narrow extends ResolveMode

  /**
   * All the arguments are typed.
   */
  case object Full extends ResolveMode

  /**
   * An argument is deferred, if it is a function literal with missing parameter types, whose parameter types
   * are derived from its expected type, and so can't be typed, before the invoked method is chosen:
   *  - a function literal with a parameter without a type, e.g. `x => x + 1`;
   *  - a placeholder function, e.g. `_ + 1`;
   *  - a pattern matching anonymous function, e.g. `{ case x => x + 1 }`;
   *  - one of the above in parentheses or braces, or as a named argument.
   */
  def isDeferred(argument: ScExpression): Boolean = argument match {
    case assignment: ScAssignment if assignment.referenceName.isDefined =>
      assignment.rightExpression.exists(isDeferred)
    case _ if ScUnderScoreSectionUtil.isUnderscoreFunction(argument) =>
      true
    case function: ScFunctionExpr =>
      function.parameters.exists(_.typeElement.isEmpty)
    case block: ScBlockExpr if block.hasCaseClauses =>
      true
    case block: ScBlockExpr =>
      block.statements match {
        case Seq(statement: ScExpression) => block.resultExpression.contains(statement) && isDeferred(statement)
        case _                            => false
      }
    case parenthesised: ScParenthesisedExpr =>
      parenthesised.innerElement.exists(isDeferred)
    case _ =>
      false
  }
}
