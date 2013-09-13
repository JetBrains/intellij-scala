package org.jetbrains.plugins.scala
package lang.rearranger

import scala.collection.immutable

/**
 * @author Roman.Shein
 * Date: 25.07.13
 */
class ScalaArrangementDependency(anchor: ScalaArrangementEntry) {
  private var methodsInfo = immutable.List[ScalaArrangementDependency]()

  def addDependentMethodInfo(info: ScalaArrangementDependency) {methodsInfo = info::methodsInfo}

  def getDependentMethodInfos = methodsInfo

  def getAnchorMethod = anchor
}
