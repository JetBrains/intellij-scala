package org.jetbrains.sbt.project.template.wizard.buildSystem

import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardStep

final class SbtScalaNewProjectWizard extends BuildSystemScalaNewProjectWizard {
  override def getName: String = SbtProjectSystem.Id.getReadableName

  override def createStep(parent: ScalaNewProjectWizardStep): SbtScalaNewProjectWizardStep =
    new SbtScalaNewProjectWizardStep(parent)

  override def getOrdinal: Int = 0
}
