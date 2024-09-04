package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardMultiStep

final class SbtScalaNewProjectWizard extends BuildSystemScalaNewProjectWizard {
  override def getName: String = NewProjectWizardConstants.BuildSystem.SBT

  override def createStep(parent: ScalaNewProjectWizardMultiStep): SbtScalaNewProjectWizardStep =
    new SbtScalaNewProjectWizardStep(parent)

  override def getOrdinal: Int = 0
}
