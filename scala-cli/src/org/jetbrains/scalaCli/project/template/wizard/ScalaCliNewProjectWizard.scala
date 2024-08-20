package org.jetbrains.scalaCli.project.template.wizard

import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardStep
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizard

final class ScalaCliNewProjectWizard extends BuildSystemScalaNewProjectWizard {

  override val getName: String = "ScalaCli"

  override def createStep(parent: ScalaNewProjectWizardStep): ScalaCliNewProjectWizardStep =
    new ScalaCliNewProjectWizardStep(parent)

  override def getOrdinal: Int = 1
}
