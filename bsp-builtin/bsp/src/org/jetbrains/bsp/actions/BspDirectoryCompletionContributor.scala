package org.jetbrains.bsp.actions

import org.jetbrains.bsp.{BSP, BspBundle}
import org.jetbrains.plugins.scala.actions.ScalaDirectoryCompletionContributorBase

final class BspDirectoryCompletionContributor
  extends ScalaDirectoryCompletionContributorBase(BSP.ProjectSystemId) {

  override def getDescription: String = BspBundle.message("bsp.sources.set")
}