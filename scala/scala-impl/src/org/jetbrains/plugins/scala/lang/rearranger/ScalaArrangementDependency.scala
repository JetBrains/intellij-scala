package org.jetbrains.plugins.scala.lang.rearranger

import org.jetbrains.annotations.Nullable

private class ScalaArrangementDependency(@Nullable anchor: ScalaArrangementEntry) {

  private var methodsInfo = List[ScalaArrangementDependency]()

  def addDependentMethodInfo(info: ScalaArrangementDependency): Unit = methodsInfo = info :: methodsInfo

  def getDependentMethodInfos: List[ScalaArrangementDependency] = methodsInfo

  @Nullable
  def getAnchorMethod: ScalaArrangementEntry = anchor
}
