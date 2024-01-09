package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardStep

final class IntelliJScalaNewProjectWizard extends BuildSystemScalaNewProjectWizard {
  override val getName: String = NewProjectWizardConstants.BuildSystem.INTELLIJ

  override def createStep(parent: ScalaNewProjectWizardStep): IntelliJScalaNewProjectWizardStep =
    new IntelliJScalaNewProjectWizardStep(parent)

  override def getOrdinal: Int = 1
}
