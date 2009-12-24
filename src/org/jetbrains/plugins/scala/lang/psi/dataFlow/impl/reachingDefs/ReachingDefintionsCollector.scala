package org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author ilyas
 */

class ReachingDefintionsCollector {

}

trait FragmentVariableInfos {
  def inputVariables: Iterable[VariableInfo]
  def outputVariables: Iterable[VariableInfo]
}

trait VariableInfo {
  def getName: String
  def getType: ScType
}