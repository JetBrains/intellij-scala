package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments

/**
 * Maps the argument on position [[argIndex]] in the argument evaluation order of a function's invocation
 * to the parameter on position [[paramIndex]] in this function's parameter list.
 */
final case class ArgParamMapping(argIndex: Int, paramIndex: Int)
