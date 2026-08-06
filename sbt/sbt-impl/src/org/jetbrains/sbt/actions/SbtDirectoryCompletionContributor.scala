package org.jetbrains.sbt.actions

import org.jetbrains.plugins.scala.actions.ScalaDirectoryCompletionContributorBase
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtProjectSystem

final class SbtDirectoryCompletionContributor
  extends ScalaDirectoryCompletionContributorBase(SbtProjectSystem.Id) {

  override def getDescription: String = SbtBundle.message("sbt.source.sets")
}