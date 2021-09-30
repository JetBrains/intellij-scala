package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

// TODO reverse it once it works: param -> arg
/**
 * Maps the argument on position [[argIndex]] in the argument evaluation order of a function's invocation
 * to the parameter on position [[paramIndex]] in this function's parameter list.
 */
final case class ArgParamMapping(argIndex: Int, paramIndex: Int)

// TODO further mapping and auto-tupling

//  def buildMapping: Seq[ArgParamMapping] = {
//    if (isTupled) Seq(ArgParamMapping(0, 0))
//    else argsMappedToParams.zipWithIndex.map { case ((_, param), index) =>
//      ArgParamMapping(argIndex = index, paramIndex = param.index)
//    }
//  }