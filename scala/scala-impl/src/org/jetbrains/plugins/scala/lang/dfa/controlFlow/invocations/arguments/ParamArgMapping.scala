package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments

// TODO change design
/**
 * Maps the parameter on position [[paramIndex]] in order in the function's parameter sequence
 * to the argument on position [[argIndex]] in the evaluation order of arguments in an invocation of this function.
 *
 * If the function has multiple parameter lists, the lists are treated as one long parameter sequence,
 * disregarding the boundaries between the original lists.
 */
final case class ParamArgMapping(paramIndex: Int, argIndex: Int)
